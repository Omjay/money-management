package com.bhaipaisa.moneymanager

import android.content.Intent
import java.time.LocalDate

/** Invented, debug-only data for emulator screenshots; never reads the vault. */
internal object DemoFixtures {
    fun stateOrNull(intent: Intent): AppState? {
        if (!intent.getBooleanExtra("hisaab_synthetic_demo", false)) return null
        val todayDate = LocalDate.now()
        val today = todayDate.toEpochDay()
        val monthly = (1..3).flatMap { monthsAgo ->
            val day = todayDate.minusMonths(monthsAgo.toLong()).plusDays(3).toEpochDay()
            listOf(
                Transaction("demo-stream-$monthsAgo", "demo-card", "credit_card", "demo-stream-$monthsAgo", "Sample Streaming", "Subscriptions", if (monthsAgo == 1) -64_900L else -49_900L, day, TransactionKind.EXPENSE),
                Transaction("demo-sip-$monthsAgo", "demo-account", "bank_account", "demo-sip-$monthsAgo", "Sample Fund SIP", "Investments", -500_000L, day + 2, TransactionKind.INVESTMENT)
            )
        }
        return AppState(
            accounts = listOf(Account("demo-account", "Sample Bank", "Savings", balancePaise = 1_357_024L, balanceDateEpochDay = today - 1)),
            cards = listOf(CreditCard("demo-card", "Sample Card", "4321")),
            loans = listOf(Loan("demo-loan", "Example contact", 82_700L, 26_450L)),
            transactions = listOf(
                Transaction("demo-1", "demo-account", "bank_account", "demo-1", "Demo Grocer", "Food & grocery", -42_861L, today - 1, TransactionKind.EXPENSE),
                Transaction("demo-2", "demo-account", "bank_account", "demo-2", "Metro Sample", "Travel", -6_582L, today - 2, TransactionKind.EXPENSE),
                Transaction("demo-3", "demo-card", "credit_card", "demo-3", "Sample Cafe", "Food & grocery", -11_736L, today - 3, TransactionKind.EXPENSE),
                Transaction("demo-4", "demo-account", "bank_account", "demo-4", "Salary Sample Employer", "Income", 8_500_000L, todayDate.withDayOfMonth(1).toEpochDay(), TransactionKind.INCOME),
                Transaction("demo-5", "demo-card", "credit_card", "demo-5", "Sample Bookshop", "Shopping", -89_900L, today - 6, TransactionKind.EXPENSE),
                Transaction("demo-6", "demo-account", "bank_account", "demo-6", "UPI/EXAMPLE PERSON/transfer", "Unreviewed debit", -120_000L, today - 4, TransactionKind.REVIEW)
            ) + monthly,
            imports = listOf(StatementImport("demo-import", "sample-statement.pdf", System.currentTimeMillis(), "Reviewed", 9, 9, 0)),
            budgets = listOf(Budget("Food & grocery", 50_000L), Budget("Travel", 30_000L)),
            holdings = listOf(
                Holding("demo-epf", "Sample EPF", "EPF", 24_500_000L, liability = false, asOfEpochDay = today - 10),
                Holding("demo-gold", "Sample gold", "Gold", 6_000_000L, liability = false, asOfEpochDay = today - 30),
                Holding("demo-bike", "Sample bike loan", "Vehicle loan", 4_200_000L, liability = true, asOfEpochDay = today - 5)
            )
        )
    }
}
