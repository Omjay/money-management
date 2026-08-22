package com.bhaipaisa.moneymanager

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SecurityRegressionTest {
    @Test
    fun invalidCreditCardDateIsRejectedWithoutCrashing() {
        val statement = """
            1234XXXX5678
            CREDIT CARD STATEMENT
            Transaction Details
            31/02/2026 1001 Example merchant 100.00
        """.trimIndent()

        assertTrue(StatementParser.parseIciciCreditCard(statement).transactions.isEmpty())
    }

    @Test
    fun validCreditCardDateStillParses() {
        val statement = """
            1234XXXX5678
            CREDIT CARD STATEMENT
            Transaction Details
            28/02/2026 1001 Example merchant 100.00
        """.trimIndent()

        assertEquals(1, StatementParser.parseIciciCreditCard(statement).transactions.size)
    }

    @Test
    fun savingsBalancesRemainIsolatedPerAccount() {
        val statement = """
            Savings A/c XXXX1111
            01-01-2026 B/F 1,000.00
            02-01-2026 Grocery 900.00
            Savings A/c XXXX2222
            01-01-2026 B/F 2,000.00
            02-01-2026 Salary 2,500.00
            Savings A/c XXXX1111
            03-01-2026 Cafe 800.00
        """.trimIndent()

        val parsed = StatementParser.parseIciciSavingsAccount(statement)
        val accountOne = parsed.transactions.filter { it.sourceEnding == "1111" }

        assertEquals(listOf(-10_000L, -10_000L), accountOne.map { it.amountPaise })
        assertEquals(80_000L, parsed.latestBalances["1111"])
        assertEquals(250_000L, parsed.latestBalances["2222"])
    }

    @Test
    fun hdfcIdentityIsProviderScopedAndCollisionResistant() {
        val first = HdfcOcrParser.accountIdentity("HDFC BANK|masked account A")
        val second = HdfcOcrParser.accountIdentity("HDFC BANK|masked account B")

        assertTrue(first.startsWith("HDFC-"))
        assertEquals(21, first.length)
        assertNotEquals(first, second)
    }

    @Test
    fun boundedCopyAcceptsLimitAndPreservesBytes() {
        val source = ByteArray(32) { it.toByte() }
        val output = ByteArrayOutputStream()

        assertEquals(source.size.toLong(), copyBounded(ByteArrayInputStream(source), output, source.size.toLong()))
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun boundedCopyRejectsOversizedInput() {
        val source = ByteArray(33)

        assertThrows(StatementRejectedException::class.java) {
            copyBounded(ByteArrayInputStream(source), ByteArrayOutputStream(), 32)
        }
    }

    @Test
    fun pdfHeaderAllowsHarmlessLeadingBytes() {
        assertTrue(hasPdfHeader("\n%PDF-1.7".encodeToByteArray()))
    }
}
