package com.bhaipaisa.moneymanager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class Account(val id: String, val name: String, val type: String, val ending: String = "", val balancePaise: Long = 0, val provider: String = "")
data class CreditCard(val id: String, val name: String, val ending: String = "", val limitPaise: Long = 0, val provider: String = "")
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

internal const val VAULT_AUTH_VALIDITY_SECONDS = 300
internal const val MAX_STATEMENT_BYTES = 20L * 1024L * 1024L
private const val IMPORT_TIMEOUT_MILLIS = 45_000L

internal fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    require(maxBytes > 0)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return total
        total += count
        if (total > maxBytes) {
            throw StatementRejectedException("The statement is larger than the ${maxBytes / (1024 * 1024)} MB limit.")
        }
        output.write(buffer, 0, count)
    }
}

internal fun hasPdfHeader(prefix: ByteArray): Boolean {
    val marker = "%PDF-".encodeToByteArray()
    if (prefix.size < marker.size) return false
    return (0..prefix.size - marker.size).any { offset ->
        marker.indices.all { index -> prefix[offset + index] == marker[index] }
    }
}

/**
 * No network client is present in this class. It stores an encrypted JSON vault
 * and encrypted imported PDF copies solely inside the app's internal storage.
 */
class FinanceStore(private val context: Context) {
    private val crypto = VaultCrypto("bhai-paisa-vault-aes-gcm-v2", authenticationRequired = true, currentFormat = true)
    private val legacyCrypto = VaultCrypto("bhai-paisa-vault-aes-gcm-v1", authenticationRequired = false, currentFormat = false)
    private val vaultFile = File(context.filesDir, "finance-vault.bin")
    private val atomicVault = AtomicFile(vaultFile)
    private val statementDir = File(context.filesDir, "statements").apply { mkdirs() }

    fun prepareForAuthentication() = crypto.prepare()

    fun load(): VaultLoad {
        if (!vaultFile.exists()) return VaultLoad(AppState())
        return runCatching {
            val payload = atomicVault.openRead().use { it.readBytes() }
            val legacy = !VaultCrypto.isCurrentPayload(payload)
            val decoded = decode((if (legacy) legacyCrypto else crypto).decrypt(payload).decodeToString())
            val state = migrateProviders(decoded)
            if (legacy || state != decoded) save(state)
            VaultLoad(state)
        }
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
        val stagedSource = try {
            stageSource(uri)
        } catch (error: StatementRejectedException) {
            return ImportOutcome(current, error.message ?: "The selected statement was rejected.")
        } catch (_: Exception) {
            return ImportOutcome(current, "The selected statement could not be copied safely.")
        }

        try {
            val parsed = try {
                withTimeout(IMPORT_TIMEOUT_MILLIS) { StatementParser.parse(context, stagedSource, password) }
            } catch (error: StatementRejectedException) {
                return ImportOutcome(current, error.message ?: "The selected statement was rejected.")
            } catch (_: Exception) {
                return ImportOutcome(current, "The statement could not be parsed safely and was not retained.")
            }
            val importId = UUID.randomUUID().toString()
            val encryptedSource = try {
                withContext(Dispatchers.IO) { encryptSource(stagedSource, importId) }
            } catch (_: Exception) {
                return ImportOutcome(current, "The statement was not saved because its encrypted copy could not be created.")
            }

            val parsedStatement = (parsed as? StatementParseResult.Success)?.statement ?: ParsedStatement(emptyList())
            val parsedTransactions = parsedStatement.transactions
            val cards = current.cards.toMutableList()
            val accounts = current.accounts.toMutableList()
            val cardBySource = cards.filter { it.provider.isNotBlank() && it.ending.isNotBlank() }
                .associateBy { providerKey(it.provider, it.ending) }.toMutableMap()
            val accountBySource = accounts.filter { it.provider.isNotBlank() && it.ending.isNotBlank() }
                .associateBy { providerKey(it.provider, it.ending) }.toMutableMap()
            parsedTransactions.filter { it.sourceType == "credit_card" }.map { it.provider to it.sourceEnding }.distinct().forEach { (provider, ending) ->
                val key = providerKey(provider, ending)
                if (key !in cardBySource) {
                    val card = CreditCard(UUID.randomUUID().toString(), "$provider Credit Card", ending, provider = provider)
                    cards += card
                    cardBySource[key] = card
                }
            }
            parsedTransactions.filter { it.sourceType == "bank_account" }.map { it.provider to it.sourceEnding }.distinct().forEach { (provider, ending) ->
                val key = providerKey(provider, ending)
                if (key !in accountBySource) {
                    val account = Account(UUID.randomUUID().toString(), "$provider Bank", "Savings", ending, provider = provider)
                    accounts += account
                    accountBySource[key] = account
                }
            }
            val balanceProvider = parsedTransactions.firstOrNull { it.sourceType == "bank_account" }?.provider
            if (balanceProvider != null) parsedStatement.latestBalances.forEach { (ending, balance) ->
                val key = providerKey(balanceProvider, ending)
                accountBySource[key]?.let { currentAccount ->
                    val updatedAccount = currentAccount.copy(balancePaise = balance)
                    val index = accounts.indexOfFirst { it.id == currentAccount.id }
                    if (index >= 0) accounts[index] = updatedAccount
                    accountBySource[key] = updatedAccount
                }
            }
            val existingReferences = current.transactions.mapTo(mutableSetOf()) { it.sourceReference }
            val newTransactions = parsedTransactions.mapNotNull { transaction ->
                val sourceReference = "${transaction.provider}:${transaction.sourceType}:${transaction.sourceEnding}:${transaction.reference}"
                if (!existingReferences.add(sourceReference)) return@mapNotNull null
                val key = providerKey(transaction.provider, transaction.sourceEnding)
                val sourceId = when (transaction.sourceType) {
                    "credit_card" -> cardBySource[key]?.id
                    "bank_account" -> accountBySource[key]?.id
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
            try {
                withContext(Dispatchers.IO) { save(updated) }
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { encryptedSource.delete() }
                return ImportOutcome(current, "The import was rolled back because the encrypted vault could not be updated.")
            }
            val message = if (parsed is StatementParseResult.Success) {
                "Saved encrypted source and added ${newTransactions.size} new transaction(s)."
            } else "Saved an encrypted source copy. $status"
            return ImportOutcome(updated, message)
        } finally {
            withContext(Dispatchers.IO) { stagedSource.delete() }
        }
    }

    private suspend fun stageSource(uri: Uri): File = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("statement-", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).use { output -> copyBounded(input, output, MAX_STATEMENT_BYTES) }
            } ?: error("Source stream unavailable")
            FileInputStream(temporary).use { input ->
                val header = ByteArray(1024)
                val count = input.read(header)
                if (count <= 0 || !hasPdfHeader(header.copyOf(count))) {
                    throw StatementRejectedException("The selected file is not a valid PDF statement.")
                }
            }
            temporary
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun encryptSource(source: File, importId: String): File {
        val temporary = File(statementDir, "$importId.tmp")
        val destination = File(statementDir, "$importId.bin")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temporary).use { output -> crypto.encryptStream(input, output) }
            }
            check(temporary.renameTo(destination)) { "Unable to finalise encrypted source" }
            return destination
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun providerKey(provider: String, ending: String): String = "${provider.uppercase(Locale.US)}|$ending"

    private fun migrateProviders(state: AppState): AppState {
        val providerBySourceId = state.transactions.mapNotNull { transaction ->
            val provider = transaction.sourceReference.substringBefore(':').takeIf { ':' in transaction.sourceReference && it.isNotBlank() }
            provider?.let { transaction.sourceId to it }
        }.toMap()
        return state.copy(
            accounts = state.accounts.map { account -> if (account.provider.isBlank()) account.copy(provider = providerBySourceId[account.id].orEmpty()) else account },
            cards = state.cards.map { card -> if (card.provider.isBlank()) card.copy(provider = providerBySourceId[card.id].orEmpty()) else card }
        )
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "Imported statement.pdf"
        }
        return "Imported statement.pdf"
    }

    private fun encode(state: AppState): String = JSONObject().apply {
        put("accounts", JSONArray(state.accounts.map { JSONObject().apply { put("id", it.id); put("name", it.name); put("type", it.type); put("ending", it.ending); put("balance", it.balancePaise); put("provider", it.provider) } }))
        put("cards", JSONArray(state.cards.map { JSONObject().apply { put("id", it.id); put("name", it.name); put("ending", it.ending); put("limit", it.limitPaise); put("provider", it.provider) } }))
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
            accounts = items("accounts") { Account(it.getString("id"), it.getString("name"), it.getString("type"), it.optString("ending"), it.optLong("balance"), it.optString("provider")) },
            cards = items("cards") { CreditCard(it.getString("id"), it.getString("name"), it.optString("ending"), it.optLong("limit"), it.optString("provider")) },
            loans = items("loans") { Loan(it.getString("id"), it.getString("person"), it.getLong("principal"), it.optLong("repaid")) },
            transactions = items("transactions") { Transaction(it.getString("id"), it.getString("sourceId"), it.getString("sourceType"), it.optString("reference", it.getString("id")), it.getString("title"), it.getString("category"), it.getLong("amount"), it.getLong("date")) },
            imports = items("imports") { StatementImport(it.getString("id"), it.getString("name"), it.getLong("at"), it.optString("status", "Imported"), it.optInt("count")) }
        )
    }
}

private class VaultCrypto(
    private val alias: String,
    private val authenticationRequired: Boolean,
    private val currentFormat: Boolean
) {
    companion object {
        private val CURRENT_MAGIC = byteArrayOf(0x42, 0x48, 0x41, 0x49, 0x50, 0x02)

        fun isCurrentPayload(payload: ByteArray): Boolean =
            payload.size > CURRENT_MAGIC.size && payload.copyOfRange(0, CURRENT_MAGIC.size).contentEquals(CURRENT_MAGIC)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (authenticationRequired) {
                builder.setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        VAULT_AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(VAULT_AUTH_VALIDITY_SECONDS)
                }
            }
            init(builder.build())
        }.generateKey()
    }

    fun prepare() {
        key()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        val prefix = if (currentFormat) CURRENT_MAGIC else byteArrayOf()
        return ByteBuffer.allocate(prefix.size + 1 + iv.size + ciphertext.size)
            .put(prefix).put(iv.size.toByte()).put(iv).put(ciphertext).array()
    }

    fun decrypt(payload: ByteArray): ByteArray {
        val offset = if (currentFormat) {
            require(isCurrentPayload(payload)) { "Invalid encrypted vault version" }
            CURRENT_MAGIC.size
        } else 0
        val buffer = ByteBuffer.wrap(payload, offset, payload.size - offset)
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize in 12..32) { "Invalid encrypted vault" }
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }.doFinal(ciphertext)
    }

    fun encryptStream(input: java.io.InputStream, output: FileOutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        if (currentFormat) output.write(CURRENT_MAGIC)
        output.write(byteArrayOf(cipher.iv.size.toByte()))
        output.write(cipher.iv)
        CipherOutputStream(output, cipher).use { encryptedOutput -> input.copyTo(encryptedOutput) }
    }
}
