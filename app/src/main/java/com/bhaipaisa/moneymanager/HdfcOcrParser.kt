package com.bhaipaisa.moneymanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class OcrLine(val page: Int, val left: Float, val top: Float, val text: String)

/** OCR fallback for image-based HDFC savings statements. Runs only on-device. */
object HdfcOcrParser {
    private val transactionDate = Regex("^\\d{2}/\\d{2}/\\d{2}$")
    private val amount = Regex("^[0-9][0-9,]*\\.[0-9]{2}$")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/uu", Locale.US)

    suspend fun parse(context: Context, uri: Uri): ParsedStatement? {
        val lines = recognisePdf(context, uri)
        if (lines.none { it.text.contains("HDFC BANK", ignoreCase = true) }) return null
        val allTransactions = mutableListOf<ParsedStatementTransaction>()
        val balances = mutableMapOf<String, Long>()
        val accountKey = "H${fingerprint(accountSignature(lines)).take(3).uppercase(Locale.US)}"
        var previousBalance: Long? = null
        lines.groupBy { it.page }.toSortedMap().forEach { (_, pageLines) ->
            val sorted = pageLines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
            val starts = sorted.indices.filter { index -> sorted[index].left < 0.16f && transactionDate.matches(sorted[index].text) }
            starts.forEachIndexed { rowIndex, start ->
                val end = starts.getOrElse(rowIndex + 1) { sorted.size }
                val row = sorted.subList(start, end)
                val date = runCatching { LocalDate.parse(row.first().text, dateFormatter) }.getOrNull() ?: return@forEachIndexed
                val numericCells = row.filter { it.left > 0.60f && amount.matches(it.text) }
                val closingCell = numericCells.maxByOrNull { it.left } ?: return@forEachIndexed
                val closing = closingCell.text.toPaise() ?: return@forEachIndexed
                balances[accountKey] = closing
                val title = row.firstOrNull { line ->
                    line.left in 0.12f..0.52f && line.text != row.first().text && !amount.matches(line.text) && !line.text.matches(Regex("^\\d{8,}$"))
                }?.text?.normalisedTitle() ?: "HDFC transaction"
                val before = previousBalance
                previousBalance = closing
                val delta = if (before != null) closing - before else initialMovement(numericCells, closingCell.left)
                if (delta == null || delta == 0L) return@forEachIndexed
                allTransactions += ParsedStatementTransaction(
                    provider = "HDFC",
                    sourceType = "bank_account",
                    sourceEnding = accountKey,
                    reference = fingerprint("$accountKey|$date|$title|$closing"),
                    title = title,
                    category = category(title, delta),
                    amountPaise = delta,
                    dateEpochDay = date.toEpochDay()
                )
            }
        }
        return if (allTransactions.isEmpty()) null else ParsedStatement(allTransactions.distinctBy { it.reference }, balances)
    }

    private suspend fun recognisePdf(context: Context, uri: Uri): List<OcrLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val lines = mutableListOf<OcrLine>()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (pageIndex in 0 until renderer.pageCount) {
                        val page = renderer.openPage(pageIndex)
                        try {
                            val scale = minOf(3f, 2048f / page.width)
                            val width = (page.width * scale).toInt()
                            val height = (page.height * scale).toInt()
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val recognised = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                                recognised.textBlocks.flatMap { it.lines }.forEach { line ->
                                    line.boundingBox?.let { box ->
                                        lines += OcrLine(pageIndex, box.left.toFloat() / width, box.top.toFloat() / height, line.text.trim())
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        } finally {
                            page.close()
                        }
                    }
                }
            }
        } finally {
            recognizer.close()
        }
        return lines.filter { it.text.isNotBlank() }
    }

    private fun accountSignature(lines: List<OcrLine>): String = lines.filter {
        it.page == 0 && it.top < 0.35f && !it.text.startsWith("From", true) && !it.text.startsWith("To", true) && !it.text.startsWith("Statement", true) && !transactionDate.matches(it.text)
    }.joinToString("|") { it.text }
    private fun String.toPaise(): Long? = (replace(",", "").toDoubleOrNull()?.times(100))?.toLong()
    private fun String.normalisedTitle(): String = replace(Regex("\\s+"), " ").trim().take(96)
    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    /** Uses the debit/deposit column only for the first row, before a prior balance exists. */
    private fun initialMovement(cells: List<OcrLine>, closingLeft: Float): Long? {
        val movement = cells.filter { it.left < closingLeft - 0.03f }.maxByOrNull { it.left } ?: return null
        val value = movement.text.toPaise() ?: return null
        return if (movement.left >= 0.76f) value else -value
    }

    private fun category(title: String, delta: Long): String {
        val normalised = title.uppercase(Locale.US)
        return when {
            listOf("SWIGGY", "ZOMATO", "INSTAMART", "GROCERY", "RESTAURANT", "CAFE").any(normalised::contains) -> "Food & grocery"
            listOf("UPI", "IMPS", "NEFT", "PAYTM", "PHONEPE").any(normalised::contains) -> "Peer transfer - review"
            listOf("GROWW", "MUTUAL", "SIP").any(normalised::contains) -> "Investments"
            delta > 0 -> "Money received"
            else -> "Miscellaneous"
        }
    }
}
