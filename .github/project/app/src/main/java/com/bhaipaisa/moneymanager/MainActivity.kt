package com.bhaipaisa.moneymanager

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var demoState: AppState? = null
    private var unlocked by mutableStateOf(false)
    private var unlockError by mutableStateOf<String?>(null)
    private var authenticationInProgress = false
    private val handler = Handler(Looper.getMainLooper())
    private val relock = Runnable { unlocked = false }
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var store: FinanceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        demoState = DemoFixtures.stateOrNull(intent)
        if (demoState == null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        store = FinanceStore(applicationContext)
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticationInProgress = false
                unlockError = null
                unlocked = true
                handler.removeCallbacks(relock)
                handler.postDelayed(relock, (VAULT_AUTH_VALIDITY_SECONDS - 10L) * 1_000L)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticationInProgress = false
                unlocked = false
                unlockError = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    "Unlock was cancelled."
                } else errString.toString()
            }
        })
        setContent {
            if (unlocked || demoState != null) HisaabApp(store, demoState) else VaultLockedScreen(unlockError, onUnlock = ::requestUnlock)
        }
    }

    override fun onStart() {
        super.onStart()
        if (demoState == null && !unlocked && !authenticationInProgress) requestUnlock()
    }

    override fun onStop() {
        handler.removeCallbacks(relock)
        if (!authenticationInProgress && !isChangingConfigurations) unlocked = false
        super.onStop()
    }

    private fun requestUnlock() {
        if (authenticationInProgress) return
        authenticationInProgress = true
        unlockError = null
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            authenticationInProgress = false
            unlockError = "Set a phone PIN, pattern, or password before using Hisaab."
            return
        }
        runCatching { store.prepareForAuthentication() }.onFailure {
            authenticationInProgress = false
            unlockError = "The secure hardware-backed vault could not be prepared on this device."
            return
        }
        val prompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hisaab")
            .setSubtitle("Use your phone's secure lock to open the local vault")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            prompt.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            prompt.setDeviceCredentialAllowed(true)
        }
        biometricPrompt.authenticate(prompt.build())
    }
}

@Composable
private fun VaultLockedScreen(error: String?, onUnlock: () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = Color(0xFF0876D1))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Hisaab is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Your local financial vault opens only after biometric or device-credential authentication.", modifier = Modifier.padding(vertical = 12.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp)) }
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}

private enum class Destination(val label: String) { HOME("Home"), ACCOUNTS("Money"), CARDS("Cards"), PEOPLE("People"), INSIGHTS("Insights") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisaabApp(store: FinanceStore, demoState: AppState? = null) {
    val loadedVault = remember { demoState?.let { VaultLoad(it) } ?: store.load() }
    var state by remember { mutableStateOf(loadedVault.state) }
    var vaultError by remember { mutableStateOf(loadedVault.error) }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val importStatement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = uri
            dialog = "import"
        }
    }
    fun update(next: AppState) {
        if (demoState != null) {
            state = next
        } else if (vaultError == null) {
            runCatching { store.save(next) }
                .onSuccess { state = next }
                .onFailure { vaultError = "The encrypted vault could not be updated. No changes were saved." }
        }
    }

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = Color(0xFF0876D1))) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (demoState == null) "Hisaab · ${destination.label}" else "Hisaab demo · ${destination.label}") }) },
            bottomBar = {
                NavigationBar {
                    listOf(Destination.HOME, Destination.ACCOUNTS, Destination.CARDS, Destination.PEOPLE, Destination.INSIGHTS).forEach { item ->
                        val icon = when (item) {
                            Destination.HOME -> Icons.Default.Home
                            Destination.ACCOUNTS -> Icons.Default.AccountBalance
                            Destination.CARDS -> Icons.Default.CreditCard
                            Destination.PEOPLE -> Icons.Default.People
                            Destination.INSIGHTS -> Icons.Default.PieChart
                        }
                        NavigationBarItem(selected = item == destination, onClick = { destination = item }, icon = { Icon(icon, item.label) }, label = { Text(item.label) })
                    }
                }
            },
            floatingActionButton = {
                when (destination) {
                    Destination.ACCOUNTS -> FloatingActionButton(onClick = { dialog = "account" }) { Icon(Icons.Default.Add, "Add account") }
                    Destination.CARDS -> FloatingActionButton(onClick = { dialog = "card" }) { Icon(Icons.Default.Add, "Add credit card") }
                    Destination.PEOPLE -> FloatingActionButton(onClick = { dialog = "loan" }) { Icon(Icons.Default.Add, "Add loan") }
                    else -> Unit
                }
            }
        ) { padding ->
            when (destination) {
                Destination.HOME -> HomeScreen(state, Modifier.padding(padding), onGo = { destination = it })
                Destination.ACCOUNTS -> AccountsScreen(state, Modifier.padding(padding), onReview = { dialog = "classify:${it.id}" })
                Destination.CARDS -> CardsScreen(state, Modifier.padding(padding), onImport = { importStatement.launch(arrayOf("application/pdf")) }, onReview = { dialog = "classify:${it.id}" })
                Destination.PEOPLE -> LoansScreen(state, Modifier.padding(padding), onRepay = { loan -> dialog = "repay:${loan.id}" })
                Destination.INSIGHTS -> InsightsScreen(state, Modifier.padding(padding), onReview = { dialog = "classify:${it.id}" }, onRemoveRule = { index -> update(state.copy(rules = state.rules.filterIndexed { itemIndex, _ -> itemIndex != index })) })
            }
        }

        when (dialog) {
            "account" -> EditorDialog("Add account", "Account name", "Savings", onDismiss = { dialog = null }) { name, type, amount ->
                update(state.copy(accounts = state.accounts + Account(UUID.randomUUID().toString(), name, type, balancePaise = amount)))
                dialog = null
            }
            "card" -> EditorDialog("Add credit card", "Card name", "Credit card", onDismiss = { dialog = null }) { name, ending, limit ->
                update(state.copy(cards = state.cards + CreditCard(UUID.randomUUID().toString(), name, ending.takeLast(4), limit)))
                dialog = null
            }
            "loan" -> EditorDialog("Add loan", "Person name", "Principal", onDismiss = { dialog = null }) { person, _, principal ->
                update(state.copy(loans = state.loans + Loan(UUID.randomUUID().toString(), person, principal)))
                dialog = null
            }
            "import" -> PasswordDialog(onDismiss = { pendingImport = null; dialog = null }) { password ->
                val uri = pendingImport
                pendingImport = null
                dialog = null
                if (uri != null && vaultError == null) scope.launch {
                    importing = true
                    try {
                        val outcome = store.importStatement(uri, password, state)
                        state = outcome.state
                        importMessage = outcome.message
                    } finally {
                        importing = false
                    }
                }
            }
            else -> if (dialog?.startsWith("classify:") == true) {
                val transactionId = dialog!!.removePrefix("classify:")
                state.transactions.firstOrNull { it.id == transactionId }?.let { transaction ->
                    ClassificationDialog(transaction, onDismiss = { dialog = null }) { kind, category, keyword ->
                        val rule = keyword?.let { ClassificationRule(it, kind, category) }
                        update(state.copy(
                            transactions = state.transactions.map { if (it.id == transactionId) it.copy(kind = kind, category = category) else it },
                            rules = if (rule == null) state.rules else state.rules + rule
                        ))
                        dialog = null
                    }
                }
            } else if (dialog?.startsWith("repay:") == true) {
                val loanId = dialog!!.removePrefix("repay:")
                AmountDialog("Record repayment", onDismiss = { dialog = null }) { amount ->
                    update(state.copy(loans = state.loans.map { if (it.id == loanId) it.copy(repaidPaise = (it.repaidPaise + amount).coerceAtMost(it.principalPaise)) else it }))
                    dialog = null
                }
            }
        }
        importMessage?.let { message ->
            AlertDialog(onDismissRequest = { importMessage = null }, title = { Text("Statement import") }, text = { Text(message) }, confirmButton = { Button(onClick = { importMessage = null }) { Text("Done") } })
        }
        if (importing) {
            AlertDialog(onDismissRequest = {}, title = { Text("Importing locally") }, text = { Text("Reading this statement on your device. No data is being sent anywhere.") }, confirmButton = {})
        }
        vaultError?.let { error ->
            AlertDialog(onDismissRequest = {}, title = { Text("Vault needs attention") }, text = { Text("$error The app is read-only until this is resolved; it will not overwrite the vault.") }, confirmButton = { Button(onClick = {}) { Text("Keep read-only") } })
        }
    }
}

@Composable
private fun HomeScreen(state: AppState, modifier: Modifier, onGo: (Destination) -> Unit) {
    val cash = state.accounts.sumOf { it.balancePaise }
    val outstanding = state.loans.sumOf { it.principalPaise - it.repaidPaise }
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeroCard("Sum of stored bank balances", money(cash), "Not live. Check each account's statement date before relying on this total.") }
        item { SectionTitle("What needs attention") }
        item { ActionCard("Credit cards", if (state.cards.isEmpty()) "No statements imported" else "${state.cards.size} card(s) · import a statement", onClick = { onGo(Destination.CARDS) }) }
        item { ActionCard("Loans", if (outstanding == 0L) "No loan recorded" else "You owe ${money(outstanding)}", onClick = { onGo(Destination.PEOPLE) }) }
        item { SectionTitle("Local-only setup") }
        item { Text("Add accounts, cards, and loans on this device. Imported PDF copies are encrypted before storage.", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun AccountsScreen(state: AppState, modifier: Modifier, onReview: (Transaction) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (state.accounts.isEmpty()) item { EmptyState("No bank accounts yet", "Use + to add an account. Balances stay on this device.") }
        items(state.accounts) { account ->
            val dateNote = account.balanceDateEpochDay?.let { "statement balance as of ${LocalDate.ofEpochDay(it)}" } ?: "balance date unknown"
            DataCard(account.name, "${account.type} · $dateNote", money(account.balancePaise))
        }
        item { SectionTitle("Recent transactions") }
        if (state.transactions.isEmpty()) item { EmptyState("No transactions yet", "Statement parsing will populate this list after import and review.") }
        items(state.transactions.sortedByDescending { it.dateEpochDay }.take(20)) { transaction -> TransactionCard(transaction, onReview) }
    }
}

@Composable
private fun CardsScreen(state: AppState, modifier: Modifier, onImport: () -> Unit, onReview: (Transaction) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Credit cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { Text("A card payment settles an earlier purchase; it is not counted as new spending.", style = MaterialTheme.typography.bodyMedium) }
        if (state.cards.isEmpty()) item { EmptyState("No credit cards yet", "Use + to add a card. Amounts remain blank until a statement is imported.") }
        items(state.cards) { card ->
            val cardTransactions = state.transactions.filter { it.sourceId == card.id }.sortedByDescending { it.dateEpochDay }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.name, fontWeight = FontWeight.SemiBold)
                    Text(if (card.ending.isBlank()) "No number stored" else "Ending ${card.ending}")
                    Text("Outstanding balance unavailable until a statement balance is reconciled. The figures below are parsed transaction totals, not the amount due.", style = MaterialTheme.typography.bodySmall)
                    MonthRow("Latest transaction month", monthlySpend(cardTransactions, 0))
                    MonthRow("Previous month", monthlySpend(cardTransactions, 1))
                    MonthRow("2 months ago", monthlySpend(cardTransactions, 2))
                    if (cardTransactions.isNotEmpty()) {
                        Text("Transactions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        cardTransactions.take(8).forEach { transaction ->
                            TransactionCard(transaction, onReview)
                        }
                    }
                }
            }
        }
        item { Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import PDF statement") } }
        item { SectionTitle("Encrypted imported statements") }
        if (state.imports.isEmpty()) item { EmptyState("Nothing imported", "The selected PDF is copied into encrypted internal storage.") }
        items(state.imports.sortedByDescending { it.importedAt }) { item ->
            val counts = if (item.candidateRows == 0 && item.parsedTransactionCount > 0) "${item.parsedTransactionCount} added · older import; coverage unknown" else "${item.candidateRows} rows found · ${item.parsedTransactionCount} added · ${item.unparsedRows} unparsed"
            DataCard(item.displayName, "${item.parseStatus} · $counts", "")
        }
    }
}

@Composable
private fun LoansScreen(state: AppState, modifier: Modifier, onRepay: (Loan) -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("People and loans", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { Text("Loans are separate from one-time peer transfers. Record each confirmed repayment against the original principal.") }
        if (state.loans.isEmpty()) item { EmptyState("No loans recorded", "Use + to add a private loan on this device.") }
        items(state.loans) { loan ->
            val outstanding = loan.principalPaise - loan.repaidPaise
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(loan.personName, fontWeight = FontWeight.SemiBold)
                    Text("I owe ${money(outstanding)}")
                    Text("Principal ${money(loan.principalPaise)} · Repaid ${money(loan.repaidPaise)}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onRepay(loan) }) { Text("Record repayment") }
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(state: AppState, modifier: Modifier, onReview: (Transaction) -> Unit, onRemoveRule: (Int) -> Unit) {
    var monthsBack by rememberSaveable { mutableStateOf(0) }
    val anchor = state.transactions.maxOfOrNull { it.dateEpochDay }?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: YearMonth.now()
    val month = anchor.minusMonths(monthsBack.toLong())
    val selected = state.transactions.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
    val expenses = selected.filter { it.kind == TransactionKind.EXPENSE && it.amountPaise < 0 }
    val income = selected.filter { it.kind == TransactionKind.INCOME && it.amountPaise > 0 }.sumOf { it.amountPaise }
    val investments = selected.filter { it.kind == TransactionKind.INVESTMENT && it.amountPaise < 0 }.sumOf { -it.amountPaise }
    val refunds = selected.filter { it.kind == TransactionKind.REFUND && it.amountPaise > 0 }.sumOf { it.amountPaise }
    val debtPayments = selected.filter { it.kind == TransactionKind.DEBT_PAYMENT && it.amountPaise < 0 }.sumOf { -it.amountPaise }
    val spending = expenses.groupBy { it.category }.mapValues { (_, value) -> value.sumOf { -it.amountPaise } }.toList().sortedByDescending { it.second }
    val review = selected.filter { it.kind == TransactionKind.REVIEW }.sortedByDescending { it.dateEpochDay }
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Monthly finances", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { monthsBack++ }) { Text("Earlier") }
            Text(month.toString(), modifier = Modifier.padding(12.dp))
            Button(onClick = { monthsBack-- }, enabled = monthsBack > 0) { Text("Later") }
        } }
        item { Text("Based only on imported and reviewed entries. Missing statement rows or periods can make totals incomplete.", style = MaterialTheme.typography.bodySmall) }
        item { DataCard("Income", "Confirmed incoming money", money(income)) }
        item { DataCard("Purchases and fees", "Excludes settlements, transfers and investments", money(expenses.sumOf { -it.amountPaise })) }
        item { DataCard("Investment contributions", "Not counted as purchases", money(investments)) }
        item { DataCard("Refunds and credits", "Shown separately from income", money(refunds)) }
        item { DataCard("Loan payments", "Not counted as purchases", money(debtPayments)) }
        item { DataCard("Card settlement entries", "May include both sides of one payment", selected.count { it.kind == TransactionKind.CARD_SETTLEMENT }.toString()) }
        item { DataCard("Transfer entries", "May include both sides of one transfer", selected.count { it.kind == TransactionKind.TRANSFER }.toString()) }
        item { SectionTitle("Purchase categories") }
        if (spending.isEmpty()) item { EmptyState("No classified purchases", "This month may have no purchases or may need review.") }
        items(spending) { (category, amount) -> DataCard(category, "Selected month", money(amount)) }
        item { SectionTitle("Needs review · ${review.size}") }
        if (review.isEmpty()) item { Text("No uncertain transactions in this month.") }
        items(review) { transaction -> TransactionCard(transaction, onReview) }
        item { SectionTitle("Saved matching rules") }
        if (state.rules.isEmpty()) item { Text("No custom rules saved.") }
        itemsIndexed(state.rules) { index, rule ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Contains: ${rule.keyword}")
                    Text("${rule.kind.label} · ${rule.category}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onRemoveRule(index) }) { Text("Remove rule") }
                }
            }
        }
    }
}

@Composable private fun HeroCard(label: String, value: String, note: String) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) { Column(Modifier.padding(20.dp)) { Text(label, color = MaterialTheme.colorScheme.onPrimary); Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary); Text(note, color = MaterialTheme.colorScheme.onPrimary) } }
@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
@Composable private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) = Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun DataCard(title: String, subtitle: String, value: String) = Card(modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Text(value, modifier = Modifier.padding(start = 12.dp).widthIn(min = 64.dp)) } }
@Composable private fun TransactionCard(transaction: Transaction, onReview: (Transaction) -> Unit) = Card(onClick = { onReview(transaction) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(transaction.title, fontWeight = FontWeight.SemiBold); Text("${transaction.kind.label} · ${transaction.category} · ${LocalDate.ofEpochDay(transaction.dateEpochDay)}", style = MaterialTheme.typography.bodySmall) }; Text(money(transaction.amountPaise), modifier = Modifier.padding(start = 12.dp).widthIn(min = 64.dp)) } }
@Composable private fun EmptyState(title: String, message: String) = Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(message, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun MonthRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.SemiBold) }

@Composable
private fun ClassificationDialog(transaction: Transaction, onDismiss: () -> Unit, onConfirm: (TransactionKind, String, String?) -> Unit) {
    var kind by remember(transaction.id) { mutableStateOf(transaction.kind) }
    var category by remember(transaction.id) { mutableStateOf(transaction.category) }
    var expanded by remember { mutableStateOf(false) }
    var rememberRule by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${transaction.title} · ${money(transaction.amountPaise)}")
                Button(onClick = { expanded = true }) { Text("Type: ${kind.label}") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    TransactionKind.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { kind = option; expanded = false })
                    }
                }
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                Row { Checkbox(checked = rememberRule, onCheckedChange = { rememberRule = it }); Text("Apply to future descriptions containing a keyword", modifier = Modifier.padding(top = 12.dp)) }
                if (rememberRule) {
                    OutlinedTextField(keyword, { keyword = it }, label = { Text("Matching keyword (at least 3 characters)") }, singleLine = true)
                    Text("Rules are saved only in the encrypted local vault. Choose a specific keyword to avoid misclassifying unrelated entries.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(kind, category.trim(), keyword.trim().takeIf { rememberRule }) }, enabled = category.isNotBlank() && (!rememberRule || keyword.trim().length >= 3)) { Text("Save locally") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditorDialog(title: String, firstLabel: String, secondLabel: String, onDismiss: () -> Unit, onConfirm: (String, String, Long) -> Unit) {
    var first by remember { mutableStateOf("") }; var second by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(first, { first = it }, label = { Text(firstLabel) }); OutlinedTextField(second, { second = it }, label = { Text(secondLabel) }); OutlinedTextField(amount, { amount = it }, label = { Text("Amount in ₹ (optional)") }) } }, confirmButton = { Button(onClick = { onConfirm(first.trim(), second.trim(), if (amount.isBlank()) 0L else requireNotNull(rupeesToPaise(amount))) }, enabled = first.isNotBlank() && (amount.isBlank() || rupeesToPaise(amount) != null)) { Text("Save locally") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun AmountDialog(title: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(amount, { amount = it }, label = { Text("Amount in ₹") }) }, confirmButton = { Button(onClick = { rupeesToPaise(amount)?.let(onConfirm) }, enabled = (rupeesToPaise(amount) ?: 0L) > 0L) { Text("Save locally") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import statement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter a PDF password only if this statement is protected. It is used for this import and is never stored.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("PDF password (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }
        },
        confirmButton = { Button(onClick = { val submitted = password; password = ""; onConfirm(submitted) }) { Text("Parse locally") } },
        dismissButton = { Button(onClick = { password = ""; onDismiss() }) { Text("Cancel") } }
    )
}

private fun money(paise: Long): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(paise / 100.0)

private fun monthlySpend(transactions: List<Transaction>, monthsBack: Long): String {
    val latestDate = transactions.maxOfOrNull { it.dateEpochDay }?.let(LocalDate::ofEpochDay) ?: return "—"
    val target = java.time.YearMonth.from(latestDate).minusMonths(monthsBack)
    val spent = transactions.filter { it.amountPaise < 0 && java.time.YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == target }.sumOf { -it.amountPaise }
    return if (spent == 0L) "—" else money(spent)
}
