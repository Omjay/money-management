package com.omjay.moneymanager

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedStatementTransaction(
    val sourceType: String,
    val sourceEnding: String,
    val reference: String,
    val title: String,
    val category: String,
    val amountPaise: Long,
    val dateEpochDay: Long
)

data class ParsedStatement(
    val transactions: List<ParsedStatementTransaction>,
    val latestBalances: Map<String, Long> = emptyMap()
)

sealed interface StatementParseResult {
    data class Success(val statement: ParsedStatement) : StatementParseResult
    data class Unsupported(val reason: String) : StatementParseResult
}

/**
 * Parses recognised text-based ICICI statements entirely on the device.
 * It deliberately does not upload a PDF, retain its password, or create a
 * transaction when a row cannot be proved from the statement structure.
 */
object StatementParser {
    private val creditDateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)
    private val bankDateFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.US)
    private val cardNumber = Regex("\\b\\d{4}X+\\d{4}\\b")
    private val bankAccount = Regex("Savings A/c\\s+([X0-9]+)", RegexOption.IGNORE_CASE)
    private val creditRowStart = Regex("^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d+)\\s+(.+)$")
    private val bankRowStart = Regex("^(\\d{2}-\\d{2}-\\d{4})\\s*(.*)$")
    private val trailingAmount = Regex("\\s+([0-9][0-9,]*\\.[0-9]{2})\\s*(CR)?\\s*$", RegexOption.IGNORE_CASE)
    private val trailingPoints = Regex("\\s+-?\\d+(?:\\s+-?\\d+)?\\s*$")
    private val currencyAmount = Regex("[0-9][0-9,]*\\.[0-9]{2}")

    fun parse(context: Context, input: InputStream, password: String?): StatementParseResult {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = try {
            PDDocument.load(input, password?.takeIf { it.isNotBlank() }).use { document ->
                PDFTextStripper().getText(document)
            }
        } catch (_: Exception) {
            return StatementParseResult.Unsupported("This PDF could not be opened. Check its password or import an unprotected statement.")
        }
        val parsed = when {
            text.contains("CREDIT CARD STATEMENT", ignoreCase = true) && text.contains("Transaction Details", ignoreCase = true) -> parseIciciCreditCard(text)
            text.contains("Statement of Transactions in Savings Account", ignoreCase = true) -> parseIciciSavingsAccount(text)
            else -> return StatementParseResult.Unsupported("This statement format is not supported yet. The encrypted source copy was still retained locally.")
        }
        return if (parsed.transactions.isEmpty()) {
            StatementParseResult.Unsupported("No transaction rows were recognised. The source copy was retained locally for a future parser update.")
        } else StatementParseResult.Success(parsed)
    }

    private fun parseIciciCreditCard(text: String): ParsedStatement {
        val lines = compactLines(text)
        val result = mutableListOf<ParsedStatementTransaction>()
        var currentCardEnding: String? = null
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            cardNumber.find(line)?.value?.let { currentCardEnding = it.takeLast(4) }
            val match = creditRowStart.matchEntire(line)
            if (match == null || currentCardEnding == null) {
                index++
                continue
            }
            val row = StringBuilder(match.groupValues[3])
            index++
            while (index < lines.size && creditRowStart.matchEntire(lines[index]) == null && cardNumber.find(lines[index]) == null) {
                val continuation = lines[index]
                if (continuation.startsWith("Credit Limit", true) || continuation.startsWith("STATEMENT", true) || continuation.startsWith("Date SerNo", true)) break
                row.append(' ').append(continuation)
                index++
            }
            parseCreditRow(match.groupValues[1], match.groupValues[2], row.toString(), currentCardEnding)?.let(result::add)
        }
        return ParsedStatement(result.distinctBy { "${it.sourceEnding}|${it.reference}" })
    }

    private fun parseCreditRow(dateText: String, reference: String, row: String, cardEnding: String): ParsedStatementTransaction? {
        val amountMatch = trailingAmount.find(row) ?: return null
        val amountPaise = paise(amountMatch.groupValues[1]) ?: return null
        val credit = amountMatch.groupValues[2].equals("CR", ignoreCase = true)
        val beforeAmount = row.substring(0, amountMatch.range.first).replace(trailingPoints, "").trim()
        if (beforeAmount.isBlank()) return null
        val title = beforeAmount.replace(Regex("\\s+"), " ").trim()
        return ParsedStatementTransaction(
            sourceType = "credit_card",
            sourceEnding = cardEnding,
            reference = reference,
            title = title,
            category = creditCardCategory(title, credit),
            amountPaise = if (credit) amountPaise else -amountPaise,
            dateEpochDay = LocalDate.parse(dateText, creditDateFormatter).toEpochDay()
        )
    }

    /** Uses balance movement, not PDF column position, to sign ICICI bank rows. */
    private fun parseIciciSavingsAccount(text: String): ParsedStatement {
        val lines = compactLines(text)
        val transactions = mutableListOf<ParsedStatementTransaction>()
        val balances = mutableMapOf<String, Long>()
        var accountEnding: String? = null
        var previousBalance: Long? = null
        var index = 0
        while (index < lines.size) {
            bankAccount.find(lines[index])?.groupValues?.getOrNull(1)?.takeLast(4)?.let { accountEnding = it }
            val match = bankRowStart.matchEntire(lines[index])
            if (match == null || accountEnding == null) {
                index++
                continue
            }
            val block = mutableListOf(match.groupValues[2])
            index++
            while (index < lines.size && bankRowStart.matchEntire(lines[index]) == null) {
                val next = lines[index]
                if (next.startsWith("Total:", true) || next.startsWith("Page ", true) || next.startsWith("Statement of", true)) break
                block += next
                index++
            }
            val amounts = currencyAmount.findAll(block.joinToString(" ")).mapNotNull { paise(it.value) }.toList()
            val closingBalance = amounts.lastOrNull() ?: continue
            balances[accountEnding] = closingBalance
            val date = LocalDate.parse(match.groupValues[1], bankDateFormatter)
            val detail = block.joinToString(" ").trim()
            if (detail.startsWith("B/F", true)) {
                previousBalance = closingBalance
                continue
            }
            val before = previousBalance
            previousBalance = closingBalance
            if (before == null) continue
            val delta = closingBalance - before
            if (delta == 0L) continue
            val title = bankTitle(block)
            val reference = fingerprint("$accountEnding|$date|$detail|$closingBalance")
            transactions += ParsedStatementTransaction(
                sourceType = "bank_account",
                sourceEnding = accountEnding,
                reference = reference,
                title = title,
                category = bankCategory(detail, delta),
                amountPaise = delta,
                dateEpochDay = date.toEpochDay()
            )
        }
        return ParsedStatement(transactions.distinctBy { "${it.sourceEnding}|${it.reference}" }, balances)
    }

    private fun bankTitle(lines: List<String>): String {
        return lines.firstOrNull { line ->
            line.isNotBlank() && !line.startsWith("UPI/", true) && !line.startsWith("IMPS/", true) && !currencyAmount.matches(line)
        }?.replace(Regex("\\s+"), " ")?.take(96) ?: "Bank transaction"
    }

    private fun creditCardCategory(title: String, credit: Boolean): String {
        val normalised = title.uppercase(Locale.US)
        if (credit && (normalised.contains("PAYMENT RECEIVED") || normalised.contains("PAYMENT THANK"))) return "Card settlement"
        if (credit) return "Refund / credit"
        return when {
            listOf("SWIGGY", "ZOMATO", "DOMINOS", "INSTAMART", "GROCERY", "RESTAURANT", "CAFE", "CHAAT").any(normalised::contains) -> "Food & grocery"
            listOf("ANTHROPIC", "MICROSOFT", "NETFLIX", "SPOTIFY", "APPLE.COM", "GOOGLE").any(normalised::contains) -> "Subscriptions"
            listOf("AMAZON", "FLIPKART", "BATA").any(normalised::contains) -> "Shopping"
            listOf("UBER", "OLA", "IRCTC", "INDIGO", "AIR", "METRO").any(normalised::contains) -> "Travel"
            listOf("IGST", "DCC FEE", "FINANCE CHARGE", "LATE FEE").any(normalised::contains) -> "Bank charges"
            else -> "Miscellaneous"
        }
    }

    private fun bankCategory(detail: String, delta: Long): String {
        val normalised = detail.uppercase(Locale.US)
        return when {
            listOf("KRITI VASUDEV", "UPI/", "IMPS/", "NEFT").any(normalised::contains) -> "Peer transfer - review"
            listOf("GROWW", "MUTUAL FUND", "SIP", "AUTOPAY").any(normalised::contains) -> "Investments"
            listOf("SWIGGY", "ZOMATO", "MCD", "RESTAURANT", "CAFE", "GROCERY").any(normalised::contains) -> "Food & grocery"
            listOf("PETROL", "FUEL", "METRO", "UBER", "OLA").any(normalised::contains) -> "Travel"
            delta > 0 -> "Money received"
            else -> "Miscellaneous"
        }
    }

    private fun compactLines(text: String): List<String> = text.lineSequence().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }.toList()
    private fun paise(amount: String): Long? = (amount.replace(",", "").toDoubleOrNull()?.times(100))?.toLong()
    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }.take(24)
}
