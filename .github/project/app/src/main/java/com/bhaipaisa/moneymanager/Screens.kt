@file:OptIn(ExperimentalMaterial3Api::class)

package com.bhaipaisa.moneymanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private const val MAX_LISTED_TRANSACTIONS = 100
/** Room under the last item for the floating Add button. */
private val SCREEN_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)
private val TAB_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp)
private val ITEM_SPACING = Arrangement.spacedBy(10.dp)

internal fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** One clickable "add something" row shown where a list would otherwise be empty. */
@Composable
private fun AddRow(title: String, detail: String, onClick: () -> Unit) = ListGroup(listOf(Unit)) { _, shape ->
    ListRow(title, detail, shape, leading = { IconBadge(Icons.Outlined.Add, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface) }, onClick = onClick)
}

/** Category colour for charts: the same category keeps the same colour across screens. */
private fun categoryColors(categories: List<String>): Map<String, Color> =
    categories.mapIndexed { index, name -> name to CategoryPalette[index % CategoryPalette.size] }.toMap()

@Composable
private fun SpendingDonutPanel(spending: List<Pair<String, Long>>, colors: Map<String, Color>, legendLimit: Int) {
    val spent = spending.sumOf { it.second }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Donut(spending.map { (name, amount) -> colors.getValue(name) to amount.toFloat() }, Modifier.width(124.dp), thickness = 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(compactMoney(spent), style = MaterialTheme.typography.titleLarge)
                    Text("spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                spending.take(legendLimit).forEach { (name, amount) -> LegendRow(colors.getValue(name), name, compactMoney(amount)) }
                if (spending.size > legendLimit) Text("+${spending.size - legendLimit} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------- Home ----------------

@Composable
internal fun HomeScreen(
    state: AppState,
    modifier: Modifier,
    onGo: (Destination) -> Unit,
    onAdd: (AddAction) -> Unit,
    onTransaction: (Transaction) -> Unit,
    onBudget: (String?) -> Unit
) {
    val worth = netWorth(state)
    val month = anchorMonth(state.transactions)
    val monthRows = state.transactions.filter { monthOf(it.dateEpochDay) == month }
    val moneyIn = monthRows.filter { it.kind == TransactionKind.INCOME && it.amountPaise > 0 }.sumOf { it.amountPaise }
    val spending = categorySpend(state.transactions, month).toList().sortedByDescending { it.second }
    val colors = categoryColors(spending.map { it.first })
    val budgets = budgetProgress(state.transactions, state.budgets, month).take(3)
    val upcoming = detectRecurring(state.transactions).filterNot { it.missed }
    val recent = state.transactions.sortedByDescending { it.dateEpochDay }.take(5)
    val toReview = state.transactions.filter { it.kind == TransactionKind.REVIEW }.sortedBy { it.dateEpochDay }
    LazyColumn(modifier.fillMaxSize(), contentPadding = SCREEN_PADDING, verticalArrangement = ITEM_SPACING) {
        item {
            HeroCard {
                Text("Net worth", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                Text(moneyRounded(worth.totalPaise), style = MaterialTheme.typography.displaySmall)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column {
                        Text("You own", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                        Text(compactMoney(worth.assetsPaise), style = MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text("You owe", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                        Text(compactMoney(worth.liabilitiesPaise), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(monthName(month), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("+${compactMoney(moneyIn)} in", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8BE3B4))
                    Text("  ·  ", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.5f))
                    Text("${compactMoney(spending.sumOf { it.second })} spent", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                QuickAction(Icons.Outlined.UploadFile, "Import", { onAdd(AddAction.IMPORT) }, Modifier.weight(1f))
                QuickAction(Icons.Outlined.AccountBalance, "Account", { onAdd(AddAction.ACCOUNT) }, Modifier.weight(1f))
                QuickAction(Icons.Outlined.DonutLarge, "Budget", { onAdd(AddAction.BUDGET) }, Modifier.weight(1f))
                QuickAction(Icons.Outlined.Savings, "Invest", { onAdd(AddAction.HOLDING) }, Modifier.weight(1f))
            }
        }
        if (toReview.isNotEmpty()) item {
            ListGroup(listOf(Unit)) { _, shape ->
                ListRow(
                    if (toReview.size == 1) "1 transaction needs a category" else "${toReview.size} transactions need a category",
                    "Sorting them keeps your totals right",
                    shape,
                    leading = { IconBadge(Icons.AutoMirrored.Outlined.HelpOutline, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) },
                    trailing = { Text("Review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) },
                    onClick = { onTransaction(toReview.first()) }
                )
            }
        }
        item { SectionHeader("Spending in ${monthName(month)}", if (spending.isNotEmpty()) "Details" else null) { onGo(Destination.INSIGHTS) } }
        item {
            if (spending.isEmpty()) EmptyState(Icons.Outlined.PieChart, "No spending yet", "Import a statement to see where your money goes, by category.", "Import statement") { onAdd(AddAction.IMPORT) }
            else SpendingDonutPanel(spending, colors, legendLimit = 4)
        }
        item { SectionHeader("Budgets", if (budgets.isNotEmpty()) "See all" else null) { onGo(Destination.INSIGHTS) } }
        item {
            if (budgets.isEmpty()) AddRow("Set a monthly budget", "See what's left to spend in each category") { onBudget(null) }
            else ListGroup(budgets) { progress, shape -> BudgetRow(progress, shape) { onBudget(progress.category) } }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("Upcoming payments", "Calendar") { onGo(Destination.INSIGHTS) } }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(upcoming, key = { it.key }) { UpcomingCard(it) } } }
        }
        item { SectionHeader("Recent activity", if (recent.isNotEmpty()) "See all" else null) { onGo(Destination.ACTIVITY) } }
        item {
            if (recent.isEmpty()) EmptyState(Icons.Outlined.UploadFile, "No transactions yet", "Import a bank or credit card statement PDF to see where your money goes.", "Import statement") { onAdd(AddAction.IMPORT) }
            else ListGroup(recent) { transaction, shape -> TransactionRow(transaction, shape, onTransaction) }
        }
    }
}

// ---------------- Activity ----------------

private fun TransactionKind?.chipLabel(): String = when (this) {
    null -> "All"
    TransactionKind.EXPENSE -> "Spent"
    TransactionKind.INCOME -> "Income"
    TransactionKind.INVESTMENT -> "Invested"
    TransactionKind.REFUND -> "Refunds"
    TransactionKind.TRANSFER -> "Transfers"
    TransactionKind.CARD_SETTLEMENT -> "Card bills"
    TransactionKind.DEBT_PAYMENT -> "Loan EMIs"
    TransactionKind.REVIEW -> "To review"
}

@Composable
internal fun ActivityScreen(state: AppState, modifier: Modifier, onTransaction: (Transaction) -> Unit, onImport: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<TransactionKind?>(null) }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceMenu by remember { mutableStateOf(false) }
    val sources = state.accounts.map { it.id to it.name } + state.cards.map { it.id to it.name }
    val results = filterTransactions(state.transactions, TransactionFilter(query, kind, sourceId))
    val days = results.take(MAX_LISTED_TRANSACTIONS).groupBy { it.dateEpochDay }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = ITEM_SPACING) {
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search merchant, category or amount", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } }) else null,
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sources.size > 1) Box {
                    FilterChip(
                        selected = sourceId != null,
                        onClick = { sourceMenu = true },
                        label = { Text(sources.firstOrNull { it.first == sourceId }?.second ?: "All accounts") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                        DropdownMenuItem(text = { Text("All accounts") }, onClick = { sourceId = null; sourceMenu = false })
                        sources.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { sourceId = id; sourceMenu = false }) }
                    }
                }
                (listOf(null) + TransactionKind.entries).forEach { option ->
                    FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.chipLabel()) })
                }
            }
        }
        when {
            state.transactions.isEmpty() -> item { EmptyState(Icons.Outlined.UploadFile, "No transactions yet", "Import a statement PDF and every entry shows up here, sorted by day.", "Import statement", onImport) }
            results.isEmpty() -> item { EmptyState(Icons.Outlined.Search, "No matches", "Try another word, an exact amount like 499, or a different filter.") }
            else -> {
                item {
                    val spent = results.filter { it.amountPaise < 0 }.sumOf { -it.amountPaise }
                    val received = results.filter { it.amountPaise > 0 }.sumOf { it.amountPaise }
                    val count = if (results.size > MAX_LISTED_TRANSACTIONS) "Latest $MAX_LISTED_TRANSACTIONS of ${results.size}" else "${results.size} transactions"
                    Footnote("$count · ${money(spent)} out · ${money(received)} in")
                }
                days.forEach { (day, rows) ->
                    item(key = "day-$day") {
                        val net = rows.sumOf { it.amountPaise }
                        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp)) {
                            Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(signedMoney(net), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "rows-$day") { ListGroup(rows) { transaction, shape -> TransactionRow(transaction, shape, onTransaction, showDate = false) } }
                }
            }
        }
    }
}

// ---------------- Accounts ----------------

private sealed interface Owed {
    data class Entered(val holding: Holding) : Owed
    data class Borrowed(val loan: Loan) : Owed
}

@Composable
internal fun AccountsScreen(
    state: AppState,
    modifier: Modifier,
    onAdd: (AddAction) -> Unit,
    onEditHolding: (Holding) -> Unit,
    onRepay: (Loan) -> Unit
) {
    val assets = state.holdings.filterNot { it.liability }.sortedByDescending { it.valuePaise }
    val owed = state.holdings.filter { it.liability }.sortedByDescending { it.valuePaise }.map { Owed.Entered(it) } + state.loans.map { Owed.Borrowed(it) }
    val bank = state.accounts.sumOf { it.balancePaise }
    val mix = (listOf("Bank accounts" to bank) + assets.groupBy { it.type }.map { (type, rows) -> type to rows.sumOf { it.valuePaise } })
        .filter { it.second > 0 }.sortedByDescending { it.second }
    val mixColors = categoryColors(mix.map { it.first })
    LazyColumn(modifier.fillMaxSize(), contentPadding = SCREEN_PADDING, verticalArrangement = ITEM_SPACING) {
        if (mix.isNotEmpty()) {
            item { SectionHeader("Where your money is") }
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Donut(mix.map { (name, amount) -> mixColors.getValue(name) to amount.toFloat() }, Modifier.width(124.dp), thickness = 14.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(compactMoney(mix.sumOf { it.second }), style = MaterialTheme.typography.titleLarge)
                                Text("you own", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            mix.take(5).forEach { (name, amount) -> LegendRow(mixColors.getValue(name), name, compactMoney(amount)) }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Bank accounts", "Add") { onAdd(AddAction.ACCOUNT) } }
        item {
            if (state.accounts.isEmpty()) AddRow("Add a bank account", "Or import a statement and it's added for you") { onAdd(AddAction.ACCOUNT) }
            else ListGroup(state.accounts) { account, shape ->
                val ending = if (account.ending.isBlank()) "" else " ••${account.ending.takeLast(4)}"
                val asOf = account.balanceDateEpochDay?.let { "as of ${shortDate(it)}" } ?: "entered by you"
                ListRow(account.name, "${account.type}$ending · $asOf", shape, leading = { IconBadge(Icons.Outlined.AccountBalance) }, trailing = { AmountText(account.balancePaise, signed = false) })
            }
        }
        item { SectionHeader("Credit cards", "Add") { onAdd(AddAction.CARD) } }
        item {
            if (state.cards.isEmpty()) AddRow("Add a credit card", "See what you spend on each card") { onAdd(AddAction.CARD) }
            else ListGroup(state.cards) { card, shape ->
                val rows = state.transactions.filter { it.sourceId == card.id }
                val month = rows.maxOfOrNull { it.dateEpochDay }?.let(::monthOf)
                ListRow(card.name, if (card.ending.isBlank()) "Credit card" else "•••• ${card.ending}", shape,
                    leading = { IconBadge(Icons.Outlined.CreditCard, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (month == null) "₹0" else money(latestMonthSpend(rows)), style = AmountStyle)
                            Text(if (month == null) "no statement" else "spent in ${shortMonth(month)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    })
            }
        }
        item { SectionHeader("Investments and assets", "Add") { onAdd(AddAction.HOLDING) } }
        item {
            if (assets.isEmpty()) AddRow("Add EPF, mutual funds, gold…", "So your net worth shows everything you own") { onAdd(AddAction.HOLDING) }
            else ListGroup(assets) { holding, shape ->
                ListRow(holding.name, "${holding.type} · updated ${shortDate(holding.asOfEpochDay)}", shape,
                    leading = { IconBadge(holdingIcon(holding), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    trailing = { AmountText(holding.valuePaise, signed = false) },
                    onClick = { onEditHolding(holding) })
            }
        }
        item { SectionHeader("Loans and money owed", "Add") { onAdd(AddAction.LOAN) } }
        item {
            if (owed.isEmpty()) AddRow("Add a loan", "Home or vehicle loans, or money borrowed from someone") { onAdd(AddAction.HOLDING) }
            else ListGroup(owed) { entry, shape ->
                when (entry) {
                    is Owed.Entered -> ListRow(entry.holding.name, "${entry.holding.type} · updated ${shortDate(entry.holding.asOfEpochDay)}", shape,
                        leading = { IconBadge(Icons.Outlined.RequestQuote, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) },
                        trailing = { AmountText(entry.holding.valuePaise, signed = false) },
                        onClick = { onEditHolding(entry.holding) })
                    is Owed.Borrowed -> ListRow("Borrowed from ${entry.loan.personName}", null, shape,
                        leading = { IconBadge(Icons.Outlined.Handshake, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RoundedProgress(if (entry.loan.principalPaise == 0L) 0f else entry.loan.repaidPaise.toFloat() / entry.loan.principalPaise, positiveColor, Modifier.padding(top = 2.dp))
                                Text("${money(entry.loan.repaidPaise)} of ${money(entry.loan.principalPaise)} repaid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailing = { AmountText(entry.loan.principalPaise - entry.loan.repaidPaise, signed = false) },
                        onClick = { onRepay(entry.loan) })
                }
            }
        }
        item { SectionHeader("Statements", "Import") { onAdd(AddAction.IMPORT) } }
        item {
            if (state.imports.isEmpty()) AddRow("Import a PDF statement", "ICICI savings and credit card, and HDFC savings for now") { onAdd(AddAction.IMPORT) }
            else ListGroup(state.imports.sortedByDescending { it.importedAt }) { statement, shape ->
                val detail = if (statement.candidateRows == 0 && statement.parsedTransactionCount > 0) "${statement.parsedTransactionCount} added"
                else "${statement.parsedTransactionCount} added · ${statement.unparsedRows} unreadable"
                ListRow(statement.displayName, "${statement.parseStatus} · $detail", shape, leading = { IconBadge(Icons.Outlined.Description, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface) })
            }
        }
        item { Footnote("Statement copies are encrypted on this phone. Balances are from the latest statement, not live from your bank.") }
    }
}

// ---------------- Insights ----------------

private enum class InsightsTab(val label: String) { SPENDING("Spending"), BUDGETS("Budgets"), RECURRING("Bills") }

@Composable
internal fun InsightsScreen(state: AppState, modifier: Modifier, onTransaction: (Transaction) -> Unit, onRemoveRule: (Int) -> Unit, onEditBudget: (String?) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(InsightsTab.SPENDING) }
    var monthsBack by rememberSaveable { mutableStateOf(0) }
    val anchor = anchorMonth(state.transactions)
    val month = anchor.minusMonths(monthsBack.toLong())
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            InsightsTab.entries.forEachIndexed { index, option ->
                SegmentedButton(selected = option == tab, onClick = { tab = option }, shape = SegmentedButtonDefaults.itemShape(index, InsightsTab.entries.size), icon = {}) { Text(option.label) }
            }
        }
        when (tab) {
            InsightsTab.SPENDING -> SpendingTab(state, anchor, month, monthsBack, { monthsBack = it }, onTransaction, onRemoveRule)
            InsightsTab.BUDGETS -> BudgetsTab(state, month, monthsBack, { monthsBack = it }, onEditBudget)
            InsightsTab.RECURRING -> RecurringTab(state)
        }
    }
}

private data class Flow(val title: String, val detail: String, val icon: ImageVector, val value: String, val positive: Boolean = false)

@Composable
private fun SpendingTab(state: AppState, anchor: YearMonth, month: YearMonth, monthsBack: Int, onMonthsBack: (Int) -> Unit, onTransaction: (Transaction) -> Unit, onRemoveRule: (Int) -> Unit) {
    val selected = state.transactions.filter { monthOf(it.dateEpochDay) == month }
    fun total(kind: TransactionKind, incoming: Boolean) = selected.filter { it.kind == kind && (it.amountPaise > 0) == incoming }.sumOf { abs(it.amountPaise) }
    val spending = categorySpend(state.transactions, month).toList().sortedByDescending { it.second }
    val spent = spending.sumOf { it.second }
    val colors = categoryColors(spending.map { it.first })
    val trend = monthlySpendTrend(state.transactions, anchor)
    val review = selected.filter { it.kind == TransactionKind.REVIEW }.sortedByDescending { it.dateEpochDay }
    val income = total(TransactionKind.INCOME, true)
    val refunds = total(TransactionKind.REFUND, true)
    val flows = listOf(
        Flow("Income", "Salary and other money in", Icons.Outlined.Payments, signedMoney(income), positive = income > 0),
        Flow("Invested", "SIPs and other contributions", Icons.AutoMirrored.Outlined.TrendingUp, money(total(TransactionKind.INVESTMENT, false))),
        Flow("Refunds", "Kept apart from income", Icons.Outlined.Replay, signedMoney(refunds), positive = refunds > 0),
        Flow("Loan payments", "Not counted as spending", Icons.Outlined.RequestQuote, money(total(TransactionKind.DEBT_PAYMENT, false))),
        Flow("Card bills and transfers", "Your own money moving", Icons.Outlined.SwapHoriz, "${selected.count { it.kind == TransactionKind.CARD_SETTLEMENT || it.kind == TransactionKind.TRANSFER }}")
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = TAB_PADDING, verticalArrangement = ITEM_SPACING) {
        item { MonthSwitcher(month, monthsBack, onMonthsBack) }
        item {
            Panel {
                Text("Last 6 months", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(spent), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 2.dp))
                Text("spent in ${monthLabel(month)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MonthBars(trend, month, onSelect = { picked -> onMonthsBack(ChronoUnit.MONTHS.between(picked, anchor).toInt()) }, Modifier.padding(top = 14.dp))
            }
        }
        if (spending.isEmpty()) item { EmptyState(Icons.Outlined.PieChart, "No spending this month", "Either nothing was bought or those transactions still need a category.") }
        else {
            item { SectionHeader("By category") }
            item { SpendingDonutPanel(spending, colors, legendLimit = 6) }
            item {
                ListGroup(spending) { (category, amount), shape ->
                    val color = colors.getValue(category)
                    val percent = if (spent == 0L) 0L else amount * 100 / spent
                    ListRow(category, "$percent% of spending", shape,
                        leading = { IconBadge(categoryIcon(category, TransactionKind.EXPENSE), color.copy(alpha = 0.16f), lerp(color, MaterialTheme.colorScheme.onSurface, 0.25f)) },
                        trailing = { AmountText(amount, signed = false) })
                }
            }
        }
        item { SectionHeader("Money in and out") }
        item {
            ListGroup(flows) { flow, shape ->
                ListRow(flow.title, flow.detail, shape, leading = { IconBadge(flow.icon, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface) },
                    trailing = { Text(flow.value, style = AmountStyle, color = if (flow.positive) positiveColor else MaterialTheme.colorScheme.onSurface) })
            }
        }
        if (review.isNotEmpty()) {
            item { SectionHeader("Needs a category · ${review.size}") }
            item { ListGroup(review) { transaction, shape -> TransactionRow(transaction, shape, onTransaction) } }
        }
        item { SectionHeader("Your rules") }
        item {
            if (state.rules.isEmpty()) Footnote("No rules yet. When you categorise a transaction, turn on \"Do this for similar transactions\" to save one.")
            else ListGroup(state.rules.withIndex().toList()) { (index, rule), shape ->
                ListRow("Contains \"${rule.keyword}\"", "${rule.kind.label} · ${rule.category}", shape,
                    trailing = { IconButton(onClick = { onRemoveRule(index) }) { Icon(Icons.Outlined.Delete, "Delete rule") } })
            }
        }
        item { Footnote("Based only on imported and reviewed transactions. A missing statement makes totals incomplete.") }
    }
}

@Composable
private fun BudgetsTab(state: AppState, month: YearMonth, monthsBack: Int, onMonthsBack: (Int) -> Unit, onEditBudget: (String?) -> Unit) {
    val progress = budgetProgress(state.transactions, state.budgets, month)
    val budgeted = state.budgets.map { it.category.lowercase() }.toSet()
    val unbudgeted = categorySpend(state.transactions, month).filterKeys { it.lowercase() !in budgeted }.toList().sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = TAB_PADDING, verticalArrangement = ITEM_SPACING) {
        item { MonthSwitcher(month, monthsBack, onMonthsBack) }
        if (progress.isEmpty()) item { EmptyState(Icons.Outlined.DonutLarge, "No budgets yet", "Set a monthly limit for categories like Food or Shopping and see what's left as you spend.", "Create a budget") { onEditBudget(null) } }
        else {
            item {
                val limit = progress.sumOf { it.limitPaise }
                val spent = progress.sumOf { it.spentPaise }
                val over = progress.count { it.overLimit }
                val fraction = if (limit == 0L) 0f else spent.toFloat() / limit
                val ringColor = if (spent > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Ring(fraction, ringColor, Modifier.width(104.dp), thickness = 12.dp) {
                            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Spent so far", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(money(spent), style = MaterialTheme.typography.headlineSmall)
                            Text("of ${money(limit)} budgeted", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when {
                                    over == 1 -> "1 budget is over"
                                    over > 1 -> "$over budgets are over"
                                    else -> "${money(limit - spent)} left"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (over > 0) MaterialTheme.colorScheme.error else positiveColor
                            )
                        }
                    }
                }
            }
            item { ListGroup(progress) { item, shape -> BudgetRow(item, shape) { onEditBudget(item.category) } } }
            item {
                FilledTonalButton(onClick = { onEditBudget(null) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("New budget")
                }
            }
        }
        if (unbudgeted.isNotEmpty()) {
            item { SectionHeader("Spending without a budget") }
            item {
                ListGroup(unbudgeted) { (category, amount), shape ->
                    ListRow(category, "Tap to set a limit", shape, leading = { IconBadge(categoryIcon(category, TransactionKind.EXPENSE), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface) },
                        trailing = { AmountText(amount, signed = false) }, onClick = { onEditBudget(category) })
                }
            }
        }
    }
}

@Composable
private fun RecurringTab(state: AppState) {
    val recurring = detectRecurring(state.transactions)
    val active = recurring.filterNot { it.missed }
    val increased = active.filter { it.priceIncreased }
    val steady = active.filterNot { it.priceIncreased }
    val missed = recurring.filter { it.missed }
    var offset by rememberSaveable { mutableStateOf(0) }
    val calendarMonth = YearMonth.now().plusMonths(offset.toLong())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = TAB_PADDING, verticalArrangement = ITEM_SPACING) {
        if (recurring.isEmpty()) item { EmptyState(Icons.Outlined.EventRepeat, "No regular payments found yet", "Import 3 or more months of statements so subscriptions, EMIs and SIPs can be spotted.") }
        else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset-- }, enabled = offset > -12) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier month") }
                    Text(if (offset == 0) "This month" else monthLabel(calendarMonth), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { offset++ }, enabled = offset < 3) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later month") }
                }
            }
            item { PaymentsCalendar(calendarMonth, recurringCalendar(state.transactions, recurring, calendarMonth)) }
            if (active.isNotEmpty()) item {
                ListGroup(listOf(Unit)) { _, shape ->
                    ListRow("Committed every month", if (active.size == 1) "Across 1 regular payment" else "Across ${active.size} regular payments", shape,
                        leading = { IconBadge(Icons.Outlined.EventRepeat) },
                        trailing = { Text(money(active.sumOf { it.monthlyEquivalentPaise }), style = AmountStyle) })
                }
            }
        }
        if (increased.isNotEmpty()) {
            item { SectionHeader("Price went up") }
            item { ListGroup(increased) { payment, shape -> RecurringRow(payment, shape) } }
        }
        if (steady.isNotEmpty()) {
            item { SectionHeader("Coming up") }
            item { ListGroup(steady) { payment, shape -> RecurringRow(payment, shape) } }
        }
        if (missed.isNotEmpty()) {
            item { SectionHeader("May have stopped") }
            item { ListGroup(missed) { payment, shape -> RecurringRow(payment, shape) } }
        }
        item { Footnote("Spotted from repeat charges to the same merchant at a steady interval and a similar amount: at least 3 monthly or 4 weekly.") }
    }
}

/** Categories offered when you categorise a transaction. */
internal fun categoryOptions(state: AppState): List<String> =
    (DEFAULT_BUDGET_CATEGORIES + listOf("Rent", "Bills & utilities", "Health", "Income", "Investments") + state.transactions.map { it.category })
        .filter { it.isNotBlank() && !it.startsWith("Unreviewed") }
        .distinctBy { it.lowercase(Locale.US) }
