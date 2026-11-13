package com.bhaipaisa.moneymanager

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/*
 * Money and date text. Kept free of Android and Compose so it can be unit tested on the JVM.
 */

private val INDIA = Locale("en", "IN")

/** Whole rupees drop the ".00", the way payment apps show amounts. */
internal fun money(paise: Long): String {
    val format = NumberFormat.getCurrencyInstance(INDIA)
    if (paise % 100 == 0L) format.maximumFractionDigits = 0
    return format.format(paise / 100.0)
}

/** Rounded to the rupee, for headline numbers and chart centres. */
internal fun moneyRounded(paise: Long): String = money((paise + if (paise >= 0) 50 else -50) / 100 * 100)

/** Short Indian units for chart labels: ₹950, ₹2.2K, ₹1.4L, ₹3.1Cr. */
internal fun compactMoney(paise: Long): String {
    val rupees = abs(paise) / 100.0
    val sign = if (paise < 0) "-" else ""
    return sign + when {
        rupees >= 1_00_00_000 -> "₹%.1fCr".format(Locale.ENGLISH, rupees / 1_00_00_000)
        rupees >= 1_00_000 -> "₹%.1fL".format(Locale.ENGLISH, rupees / 1_00_000)
        rupees >= 1_000 -> "₹%.1fK".format(Locale.ENGLISH, rupees / 1_000)
        else -> "₹%.0f".format(Locale.ENGLISH, rupees)
    }.replace(".0", "")
}

/** "+₹500" for money in, "−₹500" for money out, and a plain "₹0" when nothing moved. */
internal fun signedMoney(paise: Long): String = when {
    paise > 0 -> "+${money(paise)}"
    paise < 0 -> "−${money(-paise)}"
    else -> money(0)
}

internal fun monthLabel(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
internal fun monthName(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
internal fun shortMonth(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))

internal fun shortDate(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}

internal fun dayLabel(epochDay: Long, today: LocalDate = LocalDate.now()): String = when (epochDay) {
    today.toEpochDay() -> "Today"
    today.toEpochDay() - 1 -> "Yesterday"
    else -> LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(if (LocalDate.ofEpochDay(epochDay).year == today.year) "EEEE, d MMMM" else "d MMMM yyyy", Locale.ENGLISH))
}

/** "1 entry", "3 entries": for counts shown next to amounts, so they don't read as money. */
internal fun entryCount(count: Int): String = if (count == 1) "1 entry" else "$count entries"
