package com.bhaipaisa.moneymanager

import android.content.Intent
import java.time.LocalDate

/** Invented, debug-only data for emulator screenshots; never reads the vault. */
internal object DemoFixtures {
    fun stateOrNull(intent: Intent): AppState? {
        if (!intent.getBooleanExtra("hisaab_synthetic_demo", false)) return null
        val today = LocalDate.now().toEpochDay()
        return AppState(
            accounts = listOf(Account("demo-account", "Sample Bank", "Savings", balancePaise = 1_357_024L, balanceDateEpochDay = today - 1)),
            cards = listOf(CreditCard("demo-card", "Sample Card", "4321")),
            loans = listOf(Loan("demo-loan", "Example contact", 82_700L, 26_450L)),
            transactions = listOf(
                Transaction("demo-1", "demo-account", "bank_account", "demo-1", "Demo Grocer", "Groceries", -42_861L, today - 1, TransactionKind.EXPENSE),
                Transaction("demo-2", "demo-account", "bank_account", "demo-2", "Metro Sample", "Transport", -6_582L, today - 2, TransactionKind.EXPENSE),
                Transaction("demo-3", "demo-card", "credit_card", "demo-3", "Sample Cafe", "Food", -11_736L, today - 3, TransactionKind.EXPENSE)
            ),
            imports = listOf(StatementImport("demo-import", "sample-statement.pdf", System.currentTimeMillis(), "Reviewed", 3, 3, 0))
        )
    }
}
