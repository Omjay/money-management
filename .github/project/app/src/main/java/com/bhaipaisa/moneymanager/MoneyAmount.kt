package com.bhaipaisa.moneymanager

import java.math.BigDecimal

/** Converts decimal rupees to paise without binary floating-point truncation. */
internal fun rupeesToPaise(value: String): Long? {
    val normalised = value.trim().replace(",", "")
    if (!Regex("^[0-9]+(?:\\.[0-9]{1,2})?$").matches(normalised)) return null
    return runCatching { BigDecimal(normalised).movePointRight(2).longValueExact() }.getOrNull()
}
