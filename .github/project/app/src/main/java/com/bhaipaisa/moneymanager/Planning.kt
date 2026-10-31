package com.bhaipaisa.moneymanager

import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

/*
 * Budgets, recurring-payment detection, transaction search and net worth.
 * Everything here is derived from the local vault; nothing is fetched or inferred from outside it.
 */

internal fun monthOf(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))

/** The latest month with any transaction, so stale statements do not show an empty "current" month. */
internal fun anchorMonth(transactions: List<Transaction>): YearMonth =
    transactions.maxOfOrNull { it.dateEpochDay }?.let(::monthOf) ?: YearMonth.now()

data class BudgetProgress(val category: String, val limitPaise: Long, val spentPaise: Long) {
    val remainingPaise: Long get() = limitPaise - spentPaise
    val fraction: Float get() = if (limitPaise <= 0) 0f else spentPaise.toFloat() / limitPaise
    val overLimit: Boolean get() = spentPaise > limitPaise
}

/** Purchase spend per category; settlements, transfers, refunds and investments are excluded, matching Insights. */
internal fun categorySpend(transactions: List<Transaction>, month: YearMonth): Map<String, Long> =
    transactions.filter { it.kind == TransactionKind.EXPENSE && it.amountPaise < 0 && monthOf(it.dateEpochDay) == month }
        .groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { -it.amountPaise } }

internal fun budgetProgress(transactions: List<Transaction>, budgets: List<Budget>, month: YearMonth): List<BudgetProgress> {
    val spend = categorySpend(transactions, month)
    return budgets.map { budget ->
        BudgetProgress(budget.category, budget.monthlyLimitPaise, spend.filterKeys { it.equals(budget.category, ignoreCase = true) }.values.sum())
    }.sortedByDescending { it.fraction }
}

internal val DEFAULT_BUDGET_CATEGORIES = listOf("Food & grocery", "Shopping", "Travel", "Subscriptions", "Bank charges", "Miscellaneous")

internal fun budgetCategorySuggestions(state: AppState): List<String> =
    (DEFAULT_BUDGET_CATEGORIES + state.transactions.filter { it.kind == TransactionKind.EXPENSE }.map { it.category })
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.US) }
        .filterNot { candidate -> state.budgets.any { it.category.equals(candidate, ignoreCase = true) } }

enum class Cadence(val label: String, val approxDays: Int, val toleranceDays: Int, val minOccurrences: Int, val period: Period) {
    WEEKLY("Weekly", 7, 1, 4, Period.ofWeeks(1)),
    MONTHLY("Monthly", 30, 4, 3, Period.ofMonths(1)),
    QUARTERLY("Quarterly", 91, 7, 2, Period.ofMonths(3)),
    YEARLY("Yearly", 365, 10, 2, Period.ofYears(1))
}

data class RecurringPayment(
    val key: String,
    val title: String,
    val category: String,
    val kind: TransactionKind,
    val cadence: Cadence,
    val typicalPaise: Long,
    val latestPaise: Long,
    val previousPaise: Long,
    val occurrences: Int,
    val lastEpochDay: Long,
    val nextExpectedEpochDay: Long,
    /** The imported data runs past the expected date without a matching charge. */
    val missed: Boolean
) {
    /** More than 5% above the previous charge, e.g. a subscription that quietly went up. */
    val priceIncreased: Boolean get() = previousPaise > 0 && latestPaise * 100 > previousPaise * 105
    /** Uses the latest charge so a recent price rise is reflected in what's committed. */
    val monthlyEquivalentPaise: Long get() = latestPaise * 30 / cadence.approxDays
}

private val RECURRING_KINDS = setOf(TransactionKind.EXPENSE, TransactionKind.INVESTMENT, TransactionKind.DEBT_PAYMENT)
private const val AMOUNT_TOLERANCE_PERCENT = 35
private val MERCHANT_NOISE = setOf(
    "UPI", "NEFT", "IMPS", "RTGS", "ACH", "NACH", "ECS", "POS", "MMT", "BIL", "ONL", "INF", "PAYMENT", "PAY", "FROM",
    "THE", "AND", "PVT", "LTD", "LIMITED", "PRIVATE", "INDIA", "COM", "WWW", "AUTOPAY", "MANDATE", "DEBIT", "CARD"
)

/** A stable merchant name for grouping: letters only, rail prefixes and company suffixes removed. */
internal fun merchantKey(title: String): String =
    title.uppercase(Locale.US).split(Regex("[^A-Z]+"))
        .filter { it.length >= 3 && it !in MERCHANT_NOISE }
        .take(2)
        .joinToString(" ")

internal fun detectRecurring(transactions: List<Transaction>): List<RecurringPayment> {
    val dataThrough = transactions.maxOfOrNull { it.dateEpochDay } ?: return emptyList()
    return transactions.filter { it.amountPaise < 0 && it.kind in RECURRING_KINDS }
        .groupBy { merchantKey(it.title) }
        .filterKeys { it.isNotBlank() }
        .mapNotNull { (key, rows) -> recurringFor(key, rows, dataThrough) }
        .sortedBy { it.nextExpectedEpochDay }
}

private fun recurringFor(key: String, rows: List<Transaction>, dataThrough: Long): RecurringPayment? {
    // Same-day rows (for example a charge and its tax line) count as one occurrence.
    val charges = rows.sortedBy { it.dateEpochDay }.distinctBy { it.dateEpochDay }
    for (cadence in Cadence.entries) {
        val run = trailingRun(charges, cadence)
        if (run.size < cadence.minOccurrences) continue
        val amounts = run.map { -it.amountPaise }
        val typical = amounts.sorted()[amounts.size / 2]
        if (amounts.any { abs(it - typical) * 100 > typical * AMOUNT_TOLERANCE_PERCENT }) continue
        val last = run.last()
        val next = LocalDate.ofEpochDay(last.dateEpochDay).plus(cadence.period).toEpochDay()
        return RecurringPayment(
            key = key,
            title = last.title,
            category = last.category,
            kind = last.kind,
            cadence = cadence,
            typicalPaise = typical,
            latestPaise = amounts.last(),
            previousPaise = amounts[amounts.size - 2],
            occurrences = run.size,
            lastEpochDay = last.dateEpochDay,
            nextExpectedEpochDay = next,
            missed = dataThrough > next + cadence.toleranceDays
        )
    }
    return null
}

/** The most recent unbroken series at this cadence; older irregular history does not block detection. */
private fun trailingRun(charges: List<Transaction>, cadence: Cadence): List<Transaction> {
    var start = charges.lastIndex
    while (start > 0 && abs(charges[start].dateEpochDay - charges[start - 1].dateEpochDay - cadence.approxDays) <= cadence.toleranceDays) start--
    return charges.subList(start, charges.size)
}

/** Purchase spend for each of the [months] months ending at [through], oldest first. */
internal fun monthlySpendTrend(transactions: List<Transaction>, through: YearMonth, months: Int = 6): List<Pair<YearMonth, Long>> =
    (months - 1 downTo 0).map { back -> through.minusMonths(back.toLong()).let { month -> month to categorySpend(transactions, month).values.sum() } }

enum class CalendarStatus { PAID, UPCOMING, MISSED }

data class CalendarEntry(val day: Int, val title: String, val status: CalendarStatus)

/** Where each recurring payment falls in [month]: charges already seen, plus the next expected one. */
internal fun recurringCalendar(transactions: List<Transaction>, recurring: List<RecurringPayment>, month: YearMonth): List<CalendarEntry> =
    recurring.flatMap { payment ->
        val paid = transactions.filter { it.amountPaise < 0 && monthOf(it.dateEpochDay) == month && merchantKey(it.title) == payment.key }
            .map { it.dateEpochDay }.distinct()
            .map { CalendarEntry(LocalDate.ofEpochDay(it).dayOfMonth, payment.title, CalendarStatus.PAID) }
        val next = LocalDate.ofEpochDay(payment.nextExpectedEpochDay)
        val expected = if (YearMonth.from(next) == month) listOf(CalendarEntry(next.dayOfMonth, payment.title, if (payment.missed) CalendarStatus.MISSED else CalendarStatus.UPCOMING)) else emptyList()
        paid + expected
    }.sortedBy { it.day }

data class TransactionFilter(val query: String = "", val kind: TransactionKind? = null, val sourceId: String? = null)

/** Every search term must match the title, the category, or the exact rupee amount. */
internal fun filterTransactions(transactions: List<Transaction>, filter: TransactionFilter): List<Transaction> {
    val terms = filter.query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return transactions.filter { transaction ->
        (filter.kind == null || transaction.kind == filter.kind) &&
            (filter.sourceId == null || transaction.sourceId == filter.sourceId) &&
            terms.all { term ->
                transaction.title.contains(term, ignoreCase = true) ||
                    transaction.category.contains(term, ignoreCase = true) ||
                    rupeesToPaise(term)?.let { abs(transaction.amountPaise) == it } == true
            }
    }.sortedByDescending { it.dateEpochDay }
}

internal val ASSET_TYPES = listOf("Mutual funds", "Stocks", "EPF", "PPF", "NPS", "Fixed deposit", "Recurring deposit", "Gold", "Property", "Cash", "Other asset")
internal val LIABILITY_TYPES = listOf("Home loan", "Vehicle loan", "Personal loan", "Education loan", "Other liability")

data class NetWorth(val bankPaise: Long, val otherAssetsPaise: Long, val loansPaise: Long, val otherLiabilitiesPaise: Long) {
    val assetsPaise: Long get() = bankPaise + otherAssetsPaise
    val liabilitiesPaise: Long get() = loansPaise + otherLiabilitiesPaise
    val totalPaise: Long get() = assetsPaise - liabilitiesPaise
}

/** Credit-card dues are left out: the app never shows an amount due it cannot reconcile. */
internal fun netWorth(state: AppState): NetWorth = NetWorth(
    bankPaise = state.accounts.sumOf { it.balancePaise },
    otherAssetsPaise = state.holdings.filterNot { it.liability }.sumOf { it.valuePaise },
    loansPaise = state.loans.sumOf { (it.principalPaise - it.repaidPaise).coerceAtLeast(0) },
    otherLiabilitiesPaise = state.holdings.filter { it.liability }.sumOf { it.valuePaise }
)
