package com.bhaipaisa.moneymanager

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun signedMoneyShowsDirectionButNoSignForZero() {
        assertEquals("+₹500", signedMoney(50_000))
        assertEquals("−₹428.61", signedMoney(-42_861))
        assertEquals("₹0", signedMoney(0))
    }

    @Test
    fun moneyDropsPaiseOnlyForWholeRupees() {
        // Below one lakh, so the result is the same on the JVM and on Android (lakh grouping differs between them).
        assertEquals("₹13,570", money(1_357_000))
        assertEquals("₹13,570.24", money(1_357_024))
    }

    @Test
    fun entryCountReadsAsACountNotMoney() {
        assertEquals("0 entries", entryCount(0))
        assertEquals("1 entry", entryCount(1))
        assertEquals("3 entries", entryCount(3))
    }

    @Test
    fun dayLabelUsesRelativeNamesNearToday() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals("Today", dayLabel(today.toEpochDay(), today))
        assertEquals("Yesterday", dayLabel(today.toEpochDay() - 1, today))
        assertEquals("Monday, 9 November", dayLabel(LocalDate.of(2026, 11, 9).toEpochDay(), today))
        assertEquals("31 December 2025", dayLabel(LocalDate.of(2025, 12, 31).toEpochDay(), today))
    }
}
