package com.omjay.moneymanager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Account(val id: String, val name: String, val type: String, val ending: String = "", val balancePaise: Long = 0)
data class CreditCard(val id: String, val name: String, val ending: String = "", val limitPaise: Long = 0)
data class Loan(val id: String, val personName: String, val principalPaise: Long, val repaidPaise: Long = 0)
data class Transaction(
    val id: String,
    val sourceId: String,
    val sourceType: String,
    val sourceReference: String,
    val title: String,
    val category: String,
    val amountPaise: Long,
    val dateEpochDay: Long
)
data class StatementImport(
    val id: String,
    val displayName: String,
    val importedAt: Long,
    val parseStatus: String,
    val parsedTransactionCount: Int
)
data class AppState(
    val accounts: List<Account> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val imports: List<StatementImport> = emptyList()
)

data class VaultLoad(val state: AppState, val error: String? = null)
data class ImportOutcome(val state: AppState, val message: String)

/**
 * No network client is present in this class. It stores an encrypted JSON vault
 * and encrypted imported PDF copies solely inside the app's internal storage.
 */
class FinanceStore(private val context: Context) {
    private val crypto = VaultCrypto()
    private val vaultFile = File(context.filesDir, "finance-vault.bin")
    private val atomicVault = AtomicFile(vaultFile)
    private val statementDir = File(context.filesDir, "statements").apply { mkdirs() }

    fun load(): VaultLoad {
        if (!vaultFile.exists()) return VaultLoad(AppState())
        return runCatching { VaultLoad(decode(crypto.decrypt(atomicVault.openRead().use { it.readBytes() }).decodeToString())) }
            .getOrElse { VaultLoad(AppState(), "Your encrypted vault could not be opened. Existing files have not been changed.") }
    }

    fun save(state: AppState) {
        val output = atomicVault.startWrite()
        try {
            output.write(crypto.encrypt(encode(state).encodeToByteArray()))
            atomicVault.finishWrite(output)
        } catch (error: Exception) {
            atomicVault.failWrite(output)
            throw error
        }
    }

    suspend fun importStatement(uri: Uri, password: String?, current: AppState): ImportOutcome {
        val displayName = displayName(context.contentResolver, uri)
        val parsed = StatementParser.parse(context, uri, password)
        val importId = UUID.randomUUID().toString()
        runCatching { encryptSource(uri, importId) }.getOrElse {
            return ImportOutcome(current, "The statement was not saved because its encrypted copy could not be created.")
        }

        val parsedStatement = (parsed as? StatementParseResult.Success)?.statement ?: ParsedStatement(emptyList())
        val parsedTransactions = parsedStatement.transactions
        val cards = current.cards.toMutableList()
        val accounts = current.accounts.toMutableList()
        val cardByEnding = cards.filter { it.ending.isNotBlank() }.associateBy { it.ending }.toMutableMap()
        val accountByEnding = accounts.filter { it.ending.isNotBlank() }.associateBy { it.ending }.toMutableMap()
        parsedTransactions.filter { it.sourceType == "credit_card" }.map { it.provider to it.sourceEnding }.distinct().forEach { (provider, ending) ->
            if (ending !in cardByEnding) {
                val card = CreditCard(UUID.randomUUID().toString(), "$provider Credit Card", ending)
                cards += card
                cardByEnding[ending] = card
            }
        }
        parsedTransactions.filter { it.sourceType == "bank_account" }.map { it.provider to it.sourceEnding }.distinct().forEach { (provider, ending) ->
            if (ending !in accountByEnding) {
                val account = Account(UUID.randomUUID().toString(), "$provider Bank", "Savings", ending)
                accounts += account
                accountByEnding[ending] = account
            }
        }
        parsedStatement.latestBalances.forEach { (ending, balance) ->
            accountByEnding[ending]?.let { currentAccount ->
                val updatedAccount = currentAccount.copy(balancePaise = balance)
                val index = accounts.indexOfFirst { it.id == currentAccount.id }
                if (index >= 0) accounts[index] = updatedAccount
                accountByEnding[ending] = updatedAccount
            }
        }
        val existingReferences = current.transactions.mapTo(mutableSetOf()) { it.sourceReference }
        val newTransactions = parsedTransactions.mapNotNull { transaction ->
            val sourceReference = "${transaction.provider}:${transaction.sourceType}:${transaction.sourceEnding}:${transaction.reference}"
            if (!existingReferences.add(sourceReference)) return@mapNotNull null
            val sourceId = when (transaction.sourceType) {
                "credit_card" -> cardByEnding[transaction.sourceEnding]?.id
                "bank_account" -> accountByEnding[transaction.sourceEnding]?.id
                else -> null
            } ?: return@mapNotNull null
            Transaction(
                id = UUID.randomUUID().toString(),
                sourceId = sourceId,
                sourceType = transaction.sourceType,
                sourceReference = sourceReference,
                title = transaction.title,
                category = transaction.category,
                amountPaise = transaction.amountPaise,
                dateEpochDay = transaction.dateEpochDay
            )
        }
        val status = when (parsed) {
            is StatementParseResult.Success -> "Parsed locally"
            is StatementParseResult.Unsupported -> parsed.reason
        }
        val updated = current.copy(
            accounts = accounts,
            cards = cards,
            transactions = current.transactions + newTransactions,
            imports = current.imports + StatementImport(importId, displayName, System.currentTimeMillis(), status, newTransactions.size)
        )
        save(updated)
        val message = if (parsed is StatementParseResult.Success) {
            "Saved encrypted source and added ${newTransactions.size} new transaction(s)."
        } else "Saved an encrypted source copy. $status"
        return ImportOutcome(updated, message)
    }

    private fun encryptSource(uri: Uri, importId: String) {
        val temporary = File(statementDir, "$importId.tmp")
        val destination = File(statementDir, "$importId.bin")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).use { output -> crypto.encryptStream(input, output) }
            } ?: error("Source stream unavailable")
            check(temporary.renameTo(destination)) { "Unable to finalise encrypted source" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "Imported statement.pdf"
        }
        return "Imported statement.pdf"
    }

    private fun encode(state: AppState): String = JSONObject().apply {
        put("accounts", JSONArray(state.accounts.map { JSONObject().apply { put("id", it.id); put("name", it.name); put("type", it.type); put("ending", it.ending); put("balance", it.balancePaise) } }))
        put("cards", JSONArray(state.cards.map { JSONObject().apply { put("id", it.id); put("name", it.name); put("ending", it.ending); put("limit", it.limitPaise) } }))
        put("loans", JSONArray(state.loans.map { JSONObject().apply { put("id", it.id); put("person", it.personName); put("principal", it.principalPaise); put("repaid", it.repaidPaise) } }))
        put("transactions", JSONArray(state.transactions.map { JSONObject().apply { put("id", it.id); put("sourceId", it.sourceId); put("sourceType", it.sourceType); put("reference", it.sourceReference); put("title", it.title); put("category", it.category); put("amount", it.amountPaise); put("date", it.dateEpochDay) } }))
        put("imports", JSONArray(state.imports.map { JSONObject().apply { put("id", it.id); put("name", it.displayName); put("at", it.importedAt); put("status", it.parseStatus); put("count", it.parsedTransactionCount) } }))
    }.toString()

    private fun decode(json: String): AppState {
        val root = JSONObject(json)
        fun array(name: String) = root.optJSONArray(name) ?: JSONArray()
        fun <T> items(name: String, mapper: (JSONObject) -> T): List<T> = buildList {
            val source = array(name)
            for (index in 0 until source.length()) add(mapper(source.getJSONObject(index)))
        }
        return AppState(
            accounts = items("accounts") { Account(it.getString("id"), it.getString("name"), it.getString("type"), it.optString("ending"), it.optLong("balance")) },
            cards = items("cards") { CreditCard(it.getString("id"), it.getString("name"), it.optString("ending"), it.optLong("limit")) },
            loans = items("loans") { Loan(it.getString("id"), it.getString("person"), it.getLong("principal"), it.optLong("repaid")) },
            transactions = items("transactions") { Transaction(it.getString("id"), it.getString("sourceId"), it.getString("sourceType"), it.optString("reference", it.getString("id")), it.getString("title"), it.getString("category"), it.getLong("amount"), it.getLong("date")) },
            imports = items("imports") { StatementImport(it.getString("id"), it.getString("name"), it.getLong("at"), it.optString("status", "Imported"), it.optInt("count")) }
        )
    }
}

private class VaultCrypto {
    private val alias = "bhai-paisa-vault-aes-gcm-v1"

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + iv.size + ciphertext.size).put(iv.size.toByte()).put(iv).put(ciphertext).array()
    }

    fun decrypt(payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize in 12..32) { "Invalid encrypted vault" }
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }.doFinal(ciphertext)
    }

    fun encryptStream(input: java.io.InputStream, output: FileOutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        output.write(byteArrayOf(cipher.iv.size.toByte()))
        output.write(cipher.iv)
        CipherOutputStream(output, cipher).use { encryptedOutput -> input.copyTo(encryptedOutput) }
    }
}
