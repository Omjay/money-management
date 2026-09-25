package com.bhaipaisa.moneymanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAmountTest {
    @Test
    fun decimalAmountsDoNotLoseTheLastPaise() {
        assertEquals(29L, rupeesToPaise("0.29"))
        assertEquals(113L, rupeesToPaise("1.13"))
        assertEquals(123_456L, rupeesToPaise("1,234.56"))
    }

    @Test
    fun malformedOrFractionalPaiseAreRejected() {
        assertNull(rupeesToPaise("1.234"))
        assertNull(rupeesToPaise("not money"))
        assertNull(rupeesToPaise("9".repeat(24)))
    }
}
