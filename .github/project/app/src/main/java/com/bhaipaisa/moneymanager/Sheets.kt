@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bhaipaisa.moneymanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal enum class AddAction(val label: String, val detail: String) {
    IMPORT("Import a statement", "PDF from your bank or card, read on this phone"),
    ACCOUNT("Bank account", "Savings, current or salary account"),
    CARD("Credit card", "Track spending per card"),
    HOLDING("Investment, asset or loan", "EPF, mutual funds, gold, home loan and more"),
    LOAN("Money you borrowed", "From a friend or family member"),
    BUDGET("Monthly budget", "A spending limit for one category")
}

private fun AddAction.icon() = when (this) {
    AddAction.IMPORT -> Icons.Outlined.UploadFile
    AddAction.ACCOUNT -> Icons.Outlined.AccountBalance
    AddAction.CARD -> Icons.Outlined.CreditCard
    AddAction.HOLDING -> Icons.Outlined.Savings
    AddAction.LOAN -> Icons.Outlined.Handshake
    AddAction.BUDGET -> Icons.Outlined.DonutLarge
}

/** Every form in the app: a sheet with a title, fields, and one full-width primary action. */
@Composable
private fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = "Save",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit = {},
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
            if (confirmLabel != null) {
                Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(confirmLabel) }
            }
            secondary?.invoke()
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, supporting: String? = null) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    shape = MaterialTheme.shapes.medium,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    supportingText = supporting?.let { { Text(it) } },
    modifier = Modifier.fillMaxWidth()
)

@Composable
private fun ChoiceChips(options: List<String>, selected: String?, onSelect: (String) -> Unit) = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp)
) {
    options.forEach { option -> FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) }) }
}

@Composable
private fun SheetNote(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun AddSheet(onDismiss: () -> Unit, onPick: (AddAction) -> Unit) = FormSheet("Add to Hisaab", onDismiss, confirmLabel = null) {
    ListGroup(AddAction.entries) { action, shape ->
        ListRow(action.label, action.detail, shape, leading = { IconBadge(action.icon()) }, onClick = { onPick(action) })
    }
}

@Composable
internal fun AccountSheet(onDismiss: () -> Unit, onConfirm: (name: String, type: String, balancePaise: Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Savings") }
    var balance by remember { mutableStateOf("") }
    val paise = if (balance.isBlank()) 0L else rupeesToPaise(balance)
    FormSheet("Add bank account", onDismiss, confirmEnabled = name.isNotBlank() && paise != null, onConfirm = { onConfirm(name.trim(), type, requireNotNull(paise)) }) {
        Field(name, { name = it }, "Account name, e.g. HDFC Savings")
        ChoiceChips(listOf("Savings", "Current", "Salary", "Cash"), type) { type = it }
        Field(balance, { balance = it }, "Current balance in ₹ (optional)", KeyboardType.Decimal, "Importing a statement updates this automatically.")
    }
}

@Composable
internal fun CardSheet(onDismiss: () -> Unit, onConfirm: (name: String, ending: String, limitPaise: Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ending by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    val paise = if (limit.isBlank()) 0L else rupeesToPaise(limit)
    FormSheet("Add credit card", onDismiss, confirmEnabled = name.isNotBlank() && paise != null, onConfirm = { onConfirm(name.trim(), ending, requireNotNull(paise)) }) {
        Field(name, { name = it }, "Card name, e.g. Amazon Pay ICICI")
        Field(ending, { value -> ending = value.filter(Char::isDigit).take(4) }, "Last 4 digits (optional)", KeyboardType.Number)
        Field(limit, { limit = it }, "Credit limit in ₹ (optional)", KeyboardType.Decimal)
    }
}

@Composable
internal fun LoanSheet(onDismiss: () -> Unit, onConfirm: (person: String, principalPaise: Long) -> Unit) {
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val paise = rupeesToPaise(amount)
    FormSheet("Money you borrowed", onDismiss, confirmEnabled = person.isNotBlank() && (paise ?: 0L) > 0L, onConfirm = { onConfirm(person.trim(), requireNotNull(paise)) }) {
        Field(person, { person = it }, "Who lent it")
        Field(amount, { amount = it }, "Amount in ₹", KeyboardType.Decimal)
    }
}

@Composable
internal fun RepaySheet(loan: Loan, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    val paise = rupeesToPaise(amount)
    val outstanding = loan.principalPaise - loan.repaidPaise
    FormSheet("Pay back ${loan.personName}", onDismiss, confirmLabel = "Record repayment", confirmEnabled = (paise ?: 0L) > 0L, onConfirm = { onConfirm(requireNotNull(paise)) }) {
        Text("${money(outstanding)} still to repay of ${money(loan.principalPaise)}", style = MaterialTheme.typography.bodyLarge)
        RoundedProgress(if (loan.principalPaise == 0L) 0f else loan.repaidPaise.toFloat() / loan.principalPaise, positiveColor)
        Field(amount, { amount = it }, "Amount repaid in ₹", KeyboardType.Decimal)
    }
}

@Composable
internal fun TransactionSheet(transaction: Transaction, sourceName: String?, categorySuggestions: List<String>, onDismiss: () -> Unit, onConfirm: (TransactionKind, String, String?) -> Unit) {
    var kind by remember(transaction.id) { mutableStateOf(transaction.kind) }
    var category by remember(transaction.id) { mutableStateOf(transaction.category) }
    var rememberRule by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    FormSheet(
        title = "Transaction",
        onDismiss = onDismiss,
        confirmEnabled = category.isNotBlank() && (!rememberRule || keyword.trim().length >= 3),
        onConfirm = { onConfirm(kind, category.trim(), keyword.trim().takeIf { rememberRule }) }
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(categoryIcon(category, kind), size = 64.dp)
            Text(signedMoney(transaction.amountPaise), style = MaterialTheme.typography.displaySmall, color = if (transaction.amountPaise > 0) positiveColor else MaterialTheme.colorScheme.onSurface)
            Text(transaction.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(listOfNotNull(dayLabel(transaction.dateEpochDay), sourceName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("What kind of transaction is this?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionKind.entries.filter { it != TransactionKind.REVIEW }.forEach { option ->
                FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.label) })
            }
        }
        Field(category, { category = it }, "Category")
        if (categorySuggestions.isNotEmpty()) ChoiceChips(categorySuggestions.take(8), category) { category = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Do this for similar transactions", style = MaterialTheme.typography.bodyLarge)
                SheetNote("Future imports that contain a word you choose")
            }
            Switch(checked = rememberRule, onCheckedChange = { rememberRule = it })
        }
        if (rememberRule) Field(keyword, { keyword = it }, "Matching word, at least 3 letters", supporting = "Pick something specific, like the shop's name.")
    }
}

@Composable
internal fun BudgetSheet(existing: Budget?, initialCategory: String, suggestions: List<String>, onDismiss: () -> Unit, onRemove: () -> Unit, onConfirm: (Budget) -> Unit) {
    var category by remember { mutableStateOf(existing?.category ?: initialCategory) }
    var limit by remember { mutableStateOf(existing?.let { paiseToRupeesText(it.monthlyLimitPaise) }.orEmpty()) }
    val limitPaise = rupeesToPaise(limit)
    FormSheet(
        title = if (existing == null) "New monthly budget" else existing.category,
        onDismiss = onDismiss,
        confirmEnabled = category.isNotBlank() && (limitPaise ?: 0L) > 0L,
        onConfirm = { onConfirm(Budget(category.trim(), requireNotNull(limitPaise))) },
        secondary = if (existing != null) ({ TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Delete budget", color = MaterialTheme.colorScheme.error) } }) else null
    ) {
        if (existing == null) {
            Field(category, { category = it }, "Category")
            if (suggestions.isNotEmpty()) ChoiceChips(suggestions, category) { category = it }
        }
        Field(limit, { limit = it }, "Monthly limit in ₹", KeyboardType.Decimal)
        SheetNote("Only purchases and fees count. Card bill payments, transfers, refunds and investments don't.")
    }
}

@Composable
internal fun HoldingSheet(existing: Holding?, onDismiss: () -> Unit, onRemove: () -> Unit, onConfirm: (Holding) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: ASSET_TYPES.first()) }
    var value by remember { mutableStateOf(existing?.let { paiseToRupeesText(it.valuePaise) }.orEmpty()) }
    val valuePaise = rupeesToPaise(value)
    val liability = type in LIABILITY_TYPES
    FormSheet(
        title = if (existing == null) "Add investment, asset or loan" else "Update ${existing.name}",
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank() && valuePaise != null,
        onConfirm = { onConfirm(Holding(existing?.id ?: UUID.randomUUID().toString(), name.trim(), type, requireNotNull(valuePaise), liability, LocalDate.now().toEpochDay())) },
        secondary = if (existing != null) ({ TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Delete", color = MaterialTheme.colorScheme.error) } }) else null
    ) {
        Field(name, { name = it }, "Name, e.g. EPF or Gold coins")
        Text("What you own", style = MaterialTheme.typography.titleSmall)
        ChoiceChips(ASSET_TYPES, type) { type = it }
        Text("What you owe", style = MaterialTheme.typography.titleSmall)
        ChoiceChips(LIABILITY_TYPES, type) { type = it }
        Field(value, { value = it }, if (liability) "Amount still owed in ₹" else "Current value in ₹", KeyboardType.Decimal)
        SheetNote("Hisaab doesn't look up market prices. Update the value whenever it changes.")
    }
}

@Composable
internal fun PasswordSheet(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    FormSheet("Import statement", onDismiss, confirmLabel = "Read statement", onConfirm = { val submitted = password; password = ""; onConfirm(submitted) }) {
        Text("Statements are read on this phone. Nothing is uploaded.", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("PDF password, if it has one") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            supportingText = { Text("Used once for this import and never saved.") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun paiseToRupeesText(paise: Long): String = BigDecimal.valueOf(paise, 2).stripTrailingZeros().toPlainString()
