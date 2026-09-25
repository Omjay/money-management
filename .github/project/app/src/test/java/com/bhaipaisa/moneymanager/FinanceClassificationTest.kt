package com.bhaipaisa.moneymanager

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceClassificationTest {
    @Test
    fun cardSettlementAndRefundAreNotPurchasesOrIncome() {
        assertEquals(TransactionKind.CARD_SETTLEMENT, classifyTransaction("credit_card", "Payment received", "Card settlement", 5000).kind)
        assertEquals(TransactionKind.REFUND, classifyTransaction("credit_card", "Returned item", "Refund / credit", 5000).kind)
    }

    @Test
    fun cardBalanceReturnAndUnknownCreditAreNotIncome() {
        assertEquals(TransactionKind.TRANSFER, classifyTransaction("bank_account", "Card balance return", "Card balance transfer", 5000).kind)
        assertEquals(TransactionKind.REVIEW, classifyTransaction("bank_account", "Unknown credit", "Unreviewed credit", 5000).kind)
    }

    @Test
    fun merchantAndInvestmentTakePriorityOverGenericPaymentRail() {
        val statement = """
            Savings A/c XXXX1111
            01-01-2026 B/F 1,000.00
            02-01-2026 UPI/CAFE/SYNTHETIC 900.00
            03-01-2026 UPI/SIP/SYNTHETIC 800.00
            04-01-2026 CARD/CREDIT CARD WITHDRAWAL 850.00
        """.trimIndent()
        val parsed = StatementParser.parseIciciSavingsAccount(statement)
        assertEquals(listOf("Food & grocery", "Investments", "Card balance transfer"), parsed.transactions.map { it.category })
        assertEquals(listOf(TransactionKind.EXPENSE, TransactionKind.INVESTMENT, TransactionKind.TRANSFER), parsed.transactions.map {
            classifyTransaction(it.sourceType, it.title, it.category, it.amountPaise).kind
        })
    }

    @Test
    fun userRuleOverridesOnlyMatchingDescriptions() {
        val original = Classification(TransactionKind.REVIEW, "Uncategorised")
        val rules = listOf(ClassificationRule("specific shop", TransactionKind.EXPENSE, "Shopping"))
        assertEquals(TransactionKind.EXPENSE, applyRule(original, "UPI specific shop", rules).kind)
        assertEquals(TransactionKind.REVIEW, applyRule(original, "UPI other shop", rules).kind)
    }
}
