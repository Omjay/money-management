package com.bhaipaisa.moneymanager

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningTest {
    private var nextId = 0

    private fun row(title: String, rupees: Long, date: String, kind: TransactionKind = TransactionKind.EXPENSE, category: String = "Subscriptions", source: String = "acct") =
        Transaction("t${nextId++}", source, "bank_account", "ref$nextId", title, category, rupees * 100, LocalDate.parse(date).toEpochDay(), kind)

    @Test
    fun budgetCountsOnlyPurchasesInThatMonthAndCategory() {
        val rows = listOf(
            row("Cafe", -300, "2026-03-02", category = "Food & grocery"),
            row("Grocer", -700, "2026-03-20", category = "food & grocery"),
            row("Cafe refund", 200, "2026-03-21", TransactionKind.REFUND, "Food & grocery"),
            row("Card bill", -5000, "2026-03-22", TransactionKind.CARD_SETTLEMENT, "Food & grocery"),
            row("Cafe", -999, "2026-02-27", category = "Food & grocery")
        )
        val progress = budgetProgress(rows, listOf(Budget("Food & grocery", 80_000)), YearMonth.of(2026, 3)).single()
        assertEquals(100_000L, progress.spentPaise)
        assertTrue(progress.overLimit)
        assertEquals(-20_000L, progress.remainingPaise)
    }

    @Test
    fun monthlySubscriptionIsDetectedWithNextDateAndPriceRise() {
        val rows = listOf(
            row("UPI/NETSTREAM/autopay", -499, "2026-01-05"),
            row("UPI/NETSTREAM/autopay", -499, "2026-02-05"),
            row("UPI/NETSTREAM/autopay", -649, "2026-03-06")
        )
        val payment = detectRecurring(rows).single()
        assertEquals(Cadence.MONTHLY, payment.cadence)
        assertEquals(3, payment.occurrences)
        assertEquals(LocalDate.parse("2026-04-06").toEpochDay(), payment.nextExpectedEpochDay)
        assertTrue(payment.priceIncreased)
        assertEquals(64_900L, payment.monthlyEquivalentPaise)
        assertFalse(payment.missed)
    }

    @Test
    fun irregularOrOneOffSpendingIsNotRecurring() {
        val rows = listOf(
            row("Shopsite order", -1200, "2026-01-03", category = "Shopping"),
            row("Shopsite order", -150, "2026-01-09", category = "Shopping"),
            row("Shopsite order", -4000, "2026-02-25", category = "Shopping"),
            row("One time vendor", -800, "2026-02-01")
        )
        assertTrue(detectRecurring(rows).isEmpty())
    }

    @Test
    fun incomeAndTransfersAreNeverRecurringPayments() {
        val rows = (1..4).map { month -> row("Salary employer", 50_000, "2026-0$month-01", TransactionKind.INCOME, "Income") } +
            (1..4).map { month -> row("Self transfer", -10_000, "2026-0$month-02", TransactionKind.TRANSFER, "Transfer") }
        assertTrue(detectRecurring(rows).isEmpty())
    }

    @Test
    fun subscriptionMissingFromLaterDataIsFlagged() {
        val rows = listOf(
            row("Musicbox premium", -119, "2026-01-10"),
            row("Musicbox premium", -119, "2026-02-10"),
            row("Musicbox premium", -119, "2026-03-10"),
            row("Cafe", -200, "2026-05-30", category = "Food & grocery")
        )
        assertTrue(detectRecurring(rows).single().missed)
    }

    @Test
    fun spendTrendCoversEachMonthIncludingEmptyOnes() {
        val rows = listOf(
            row("Cafe", -300, "2026-01-10", category = "Food & grocery"),
            row("Cafe", -200, "2026-03-10", category = "Food & grocery"),
            row("Salary", 9000, "2026-03-01", TransactionKind.INCOME, "Income")
        )
        val trend = monthlySpendTrend(rows, YearMonth.of(2026, 3), months = 3)
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), trend.map { it.first })
        assertEquals(listOf(30_000L, 0L, 20_000L), trend.map { it.second })
    }

    @Test
    fun calendarMarksPaidChargesAndTheNextExpectedOne() {
        val rows = listOf(
            row("UPI/NETSTREAM/autopay", -499, "2026-01-05"),
            row("UPI/NETSTREAM/autopay", -499, "2026-02-05"),
            row("UPI/NETSTREAM/autopay", -499, "2026-03-05")
        )
        val recurring = detectRecurring(rows)
        val march = recurringCalendar(rows, recurring, YearMonth.of(2026, 3))
        assertEquals(listOf(CalendarEntry(5, "UPI/NETSTREAM/autopay", CalendarStatus.PAID)), march)
        val april = recurringCalendar(rows, recurring, YearMonth.of(2026, 4))
        assertEquals(listOf(CalendarEntry(5, "UPI/NETSTREAM/autopay", CalendarStatus.UPCOMING)), april)
    }

    @Test
    fun merchantKeyIgnoresPaymentRailNoise() {
        assertEquals(merchantKey("UPI/NETSTREAM/123456/autopay"), merchantKey("NACH NETSTREAM 998877"))
    }

    @Test
    fun searchMatchesTitleCategoryAndExactAmount() {
        val rows = listOf(
            row("Metro card recharge", -250, "2026-03-01", category = "Travel"),
            row("Cafe", -499, "2026-03-02", category = "Food & grocery"),
            row("Bookstore", -499, "2026-03-03", category = "Shopping", source = "card")
        )
        assertEquals(listOf("Metro card recharge"), filterTransactions(rows, TransactionFilter("metro")).map { it.title })
        assertEquals(listOf("Cafe"), filterTransactions(rows, TransactionFilter("food")).map { it.title })
        assertEquals(listOf("Bookstore", "Cafe"), filterTransactions(rows, TransactionFilter("499")).map { it.title })
        assertEquals(listOf("Bookstore"), filterTransactions(rows, TransactionFilter("499", sourceId = "card")).map { it.title })
        assertTrue(filterTransactions(rows, TransactionFilter(kind = TransactionKind.INCOME)).isEmpty())
    }

    @Test
    fun netWorthSubtractsLoansAndLiabilities() {
        val state = AppState(
            accounts = listOf(Account("a", "Bank", "Savings", balancePaise = 100_000)),
            loans = listOf(Loan("l", "Friend", 30_000, 10_000)),
            holdings = listOf(
                Holding("h1", "EPF", "EPF", 500_000, liability = false, asOfEpochDay = 0),
                Holding("h2", "Bike loan", "Vehicle loan", 200_000, liability = true, asOfEpochDay = 0)
            )
        )
        val worth = netWorth(state)
        assertEquals(600_000L, worth.assetsPaise)
        assertEquals(220_000L, worth.liabilitiesPaise)
        assertEquals(380_000L, worth.totalPaise)
    }
}
