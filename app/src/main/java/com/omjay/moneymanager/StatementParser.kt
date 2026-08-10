package com.omjay.moneymanager

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedStatementTransaction(
    val cardEnding: String,
    val reference: String,
    val title: String,
    val category: String,
    val amountPaise: Long,
    val dateEpochDay: Long
)

sealed interface StatementParseResult {
    data class Success(val transactions: List<ParsedStatementTransaction>) : StatementParseResult
    data class Unsupported(val reason: String) : StatementParseResult
}

/**
 * Parses text-based ICICI credit-card statements entirely on the device.
 * It deliberately does not upload a PDF, persist its password, or try to infer
 * a transaction when the table structure is not recognised.
 */
object StatementParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)
    private val cardNumber = Regex("\\b\\d{4}X+\\d{4}\\b")
    private val rowStart = Regex("^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d+)\\s+(.+)$")
    private val trailingAmount = Regex("\\s+([0-9][0-9,]*\\.[0-9]{2})\\s*(CR)?\\s*$", RegexOption.IGNORE_CASE)
    private val trailingPoints = Regex("\\s+-?\\d+(?:\\s+-?\\d+)?\\s*$")

    fun parse(context: Context, input: InputStream, password: String?): StatementParseResult {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = try {
            PDDocument.load(input, password?.takeIf { it.isNotBlank() }).use { document ->
                PDFTextStripper().getText(document)
            }
        } catch (_: Exception) {
            return StatementParseResult.Unsupported("This PDF could not be opened. Check its password or import an unprotected statement.")
        }
        if (!text.contains("CREDIT CARD STATEMENT", ignoreCase = true) || !text.contains("Transaction Details", ignoreCase = true)) {
            return StatementParseResult.Unsupported("This statement format is not supported yet. The encrypted source copy was still retained locally.")
        }
        val transactions = parseIciciCreditCard(text)
        return if (transactions.isEmpty()) {
            StatementParseResult.Unsupported("No transaction rows were recognised. The source copy was retained locally for a future parser update.")
        } else StatementParseResult.Success(transactions)
    }

    private fun parseIciciCreditCard(text: String): List<ParsedStatementTransaction> {
        val lines = text.lineSequence().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }.toList()
        val result = mutableListOf<ParsedStatementTransaction>()
        var currentCardEnding: String? = null
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            cardNumber.find(line)?.value?.let { currentCardEnding = it.takeLast(4) }
            val match = rowStart.matchEntire(line)
            if (match == null || currentCardEnding == null) {
                index++
                continue
            }

            val row = StringBuilder(match.groupValues[3])
            index++
            while (index < lines.size && rowStart.matchEntire(lines[index]) == null && cardNumber.find(lines[index]) == null) {
                val continuation = lines[index]
                if (continuation.startsWith("Credit Limit", true) || continuation.startsWith("STATEMENT", true) || continuation.startsWith("Date SerNo", true)) break
                row.append(' ').append(continuation)
                index++
            }
            parseRow(match.groupValues[1], match.groupValues[2], row.toString(), currentCardEnding)?.let(result::add)
        }
        return result.distinctBy { "${it.cardEnding}|${it.reference}" }
    }

    private fun parseRow(dateText: String, reference: String, row: String, cardEnding: String): ParsedStatementTransaction? {
        val amountMatch = trailingAmount.find(row) ?: return null
        val amountPaise = ((amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null) * 100).toLong()
        val credit = amountMatch.groupValues[2].equals("CR", ignoreCase = true)
        val beforeAmount = row.substring(0, amountMatch.range.first).replace(trailingPoints, "").trim()
        if (beforeAmount.isBlank()) return null
        val title = beforeAmount.replace(Regex("\\s+"), " ").trim()
        return ParsedStatementTransaction(
            cardEnding = cardEnding,
            reference = reference,
            title = title,
            category = categoryFor(title, credit),
            amountPaise = if (credit) amountPaise else -amountPaise,
            dateEpochDay = LocalDate.parse(dateText, dateFormatter).toEpochDay()
        )
    }

    private fun categoryFor(title: String, credit: Boolean): String {
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
}
