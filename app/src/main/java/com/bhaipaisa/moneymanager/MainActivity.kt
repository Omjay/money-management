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
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var unlockError by mutableStateOf<String?>(null)
    private var authenticationInProgress = false
    private val handler = Handler(Looper.getMainLooper())
    private val relock = Runnable { unlocked = false }
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var store: FinanceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
            if (unlocked) MoneyManagerApp(store) else VaultLockedScreen(unlockError, onUnlock = ::requestUnlock)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!unlocked && !authenticationInProgress) requestUnlock()
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
            unlockError = "Set a phone PIN, pattern, or password before using BhaiPaisa."
            return
        }
        runCatching { store.prepareForAuthentication() }.onFailure {
            authenticationInProgress = false
            unlockError = "The secure hardware-backed vault could not be prepared on this device."
            return
        }
        val prompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock BhaiPaisa")
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
            Text("BhaiPaisa is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Your local financial vault opens only after biometric or device-credential authentication.", modifier = Modifier.padding(vertical = 12.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp)) }
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}

private enum class Destination(val label: String) { HOME("Home"), ACCOUNTS("Money"), CARDS("Cards"), PEOPLE("People"), INSIGHTS("Insights") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneyManagerApp(store: FinanceStore) {
    val loadedVault = remember { store.load() }
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
        if (vaultError == null) {
            runCatching { store.save(next) }
                .onSuccess { state = next }
                .onFailure { vaultError = "The encrypted vault could not be updated. No changes were saved." }
        }
    }

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = Color(0xFF0876D1))) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(destination.label) }) },
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
                Destination.ACCOUNTS -> AccountsScreen(state, Modifier.padding(padding))
                Destination.CARDS -> CardsScreen(state, Modifier.padding(padding), onImport = { importStatement.launch(arrayOf("application/pdf")) })
                Destination.PEOPLE -> LoansScreen(state, Modifier.padding(padding), onRepay = { loan -> dialog = "repay:${loan.id}" })
                Destination.INSIGHTS -> InsightsScreen(state, Modifier.padding(padding))
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
            else -> if (dialog?.startsWith("repay:") == true) {
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
        item { HeroCard("Money available now", money(cash), "Local vault · no network access") }
        item { SectionTitle("What needs attention") }
        item { ActionCard("Credit cards", if (state.cards.isEmpty()) "No statements imported" else "${state.cards.size} card(s) · import a statement", onClick = { onGo(Destination.CARDS) }) }
        item { ActionCard("Loans", if (outstanding == 0L) "No loan recorded" else "You owe ${money(outstanding)}", onClick = { onGo(Destination.PEOPLE) }) }
        item { SectionTitle("Local-only setup") }
        item { Text("Add accounts, cards, and loans on this device. Imported PDF copies are encrypted before storage.", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun AccountsScreen(state: AppState, modifier: Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (state.accounts.isEmpty()) item { EmptyState("No bank accounts yet", "Use + to add an account. Balances stay on this device.") }
        items(state.accounts) { account -> DataCard(account.name, "${account.type} · local balance", money(account.balancePaise)) }
        item { SectionTitle("Recent transactions") }
        if (state.transactions.isEmpty()) item { EmptyState("No transactions yet", "Statement parsing will populate this list after import and review.") }
        items(state.transactions.sortedByDescending { it.dateEpochDay }.take(20)) { transaction -> DataCard(transaction.title, transaction.category, money(transaction.amountPaise)) }
    }
}

@Composable
private fun CardsScreen(state: AppState, modifier: Modifier, onImport: () -> Unit) {
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
                    Text(if (cardTransactions.isEmpty()) "Amounts stay blank until a statement is imported." else "Tracked card balance ${money(cardTransactions.sumOf { it.amountPaise })}", style = MaterialTheme.typography.bodySmall)
                    MonthRow("This statement month", monthlySpend(cardTransactions, 0))
                    MonthRow("Previous month", monthlySpend(cardTransactions, 1))
                    MonthRow("2 months ago", monthlySpend(cardTransactions, 2))
                    if (cardTransactions.isNotEmpty()) {
                        Text("Transactions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        cardTransactions.take(8).forEach { transaction ->
                            DataCard(transaction.title, "${transaction.category} • ${LocalDate.ofEpochDay(transaction.dateEpochDay)}", money(transaction.amountPaise))
                        }
                    }
                }
            }
        }
        item { Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import PDF statement") } }
        item { SectionTitle("Encrypted imported statements") }
        if (state.imports.isEmpty()) item { EmptyState("Nothing imported", "The selected PDF is copied into encrypted internal storage.") }
        items(state.imports.sortedByDescending { it.importedAt }) { item -> DataCard(item.displayName, item.parseStatus, "${item.parsedTransactionCount} added") }
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
private fun InsightsScreen(state: AppState, modifier: Modifier) {
    val spending = state.transactions.filter { it.amountPaise < 0 }.groupBy { it.category }.mapValues { (_, value) -> value.sumOf { -it.amountPaise } }.toList().sortedByDescending { it.second }
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Spending insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { Text("Money received and money spent will appear here after statements are parsed and reviewed.") }
        if (spending.isEmpty()) item { EmptyState("No spending data", "Import a statement, then review its transaction categories.") }
        items(spending) { (category, amount) -> DataCard(category, "Monthly spent", money(amount)) }
    }
}

@Composable private fun HeroCard(label: String, value: String, note: String) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) { Column(Modifier.padding(20.dp)) { Text(label, color = MaterialTheme.colorScheme.onPrimary); Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary); Text(note, color = MaterialTheme.colorScheme.onPrimary) } }
@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
@Composable private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) = Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun DataCard(title: String, subtitle: String, value: String) = Card(modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Text(value, modifier = Modifier.padding(start = 12.dp).widthIn(min = 64.dp)) } }
@Composable private fun EmptyState(title: String, message: String) = Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(message, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun MonthRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.SemiBold) }

@Composable
private fun EditorDialog(title: String, firstLabel: String, secondLabel: String, onDismiss: () -> Unit, onConfirm: (String, String, Long) -> Unit) {
    var first by remember { mutableStateOf("") }; var second by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(first, { first = it }, label = { Text(firstLabel) }); OutlinedTextField(second, { second = it }, label = { Text(secondLabel) }); OutlinedTextField(amount, { amount = it }, label = { Text("Amount in ₹ (optional)") }) } }, confirmButton = { Button(onClick = { if (first.isNotBlank()) onConfirm(first.trim(), second.trim(), (amount.toDoubleOrNull()?.times(100))?.toLong() ?: 0L) }) { Text("Save locally") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun AmountDialog(title: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(amount, { amount = it }, label = { Text("Amount in ₹") }) }, confirmButton = { Button(onClick = { amount.toDoubleOrNull()?.let { if (it > 0) onConfirm((it * 100).toLong()) } }) { Text("Save locally") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
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
