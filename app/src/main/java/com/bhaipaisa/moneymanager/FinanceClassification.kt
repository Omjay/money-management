package com.bhaipaisa.moneymanager

import java.util.Locale

enum class TransactionKind(val label: String) {
    EXPENSE("Purchase or fee"),
    INCOME("Income"),
    TRANSFER("Own-account transfer"),
    CARD_SETTLEMENT("Card settlement"),
    INVESTMENT("Investment contribution"),
    REFUND("Refund or credit"),
    DEBT_PAYMENT("Loan payment"),
    REVIEW("Needs review");

    companion object {
        fun fromStored(value: String): TransactionKind = entries.firstOrNull { it.name == value } ?: REVIEW
    }
}

data class ClassificationRule(val keyword: String, val kind: TransactionKind, val category: String)

data class Classification(val kind: TransactionKind, val category: String)

internal fun classifyTransaction(sourceType: String, title: String, parsedCategory: String, amountPaise: Long): Classification {
    val normalised = title.uppercase(Locale.US)
    if (sourceType == "credit_card") {
        return when {
            parsedCategory == "Card settlement" -> Classification(TransactionKind.CARD_SETTLEMENT, parsedCategory)
            amountPaise > 0 -> Classification(TransactionKind.REFUND, parsedCategory)
            else -> Classification(TransactionKind.EXPENSE, parsedCategory)
        }
    }
    return when {
        parsedCategory == "Card balance transfer" -> Classification(TransactionKind.TRANSFER, parsedCategory)
        parsedCategory == "Card settlement" -> Classification(TransactionKind.CARD_SETTLEMENT, parsedCategory)
        parsedCategory == "Investments" -> Classification(TransactionKind.INVESTMENT, parsedCategory)
        parsedCategory == "Refund / credit" -> Classification(TransactionKind.REFUND, parsedCategory)
        parsedCategory == "Income" -> Classification(TransactionKind.INCOME, parsedCategory)
        listOf("Food & grocery", "Subscriptions", "Shopping", "Travel", "Bank charges").contains(parsedCategory) && amountPaise < 0 -> Classification(TransactionKind.EXPENSE, parsedCategory)
        normalised.contains("LOAN EMI") || normalised.contains("LOAN REPAYMENT") -> Classification(TransactionKind.DEBT_PAYMENT, "Loan payment")
        else -> Classification(TransactionKind.REVIEW, parsedCategory)
    }
}

internal fun applyRule(classification: Classification, title: String, rules: List<ClassificationRule>): Classification {
    val rule = rules.lastOrNull { it.keyword.isNotBlank() && title.contains(it.keyword, ignoreCase = true) }
    return if (rule == null) classification else Classification(rule.kind, rule.category)
}
