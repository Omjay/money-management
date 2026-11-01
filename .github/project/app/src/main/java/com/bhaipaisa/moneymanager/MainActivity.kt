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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            HisaabTheme {
                if (unlocked || demoState != null) HisaabApp(store, demoState, onLock = { handler.removeCallbacks(relock); unlocked = false })
                else VaultLockedScreen(unlockError, onUnlock = ::requestUnlock)
            }
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
private fun AppMark() = Box(
    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(HeroBrush),
    contentAlignment = Alignment.Center
) {
    Icon(Icons.Outlined.CurrencyRupee, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
}

@Composable
private fun VaultLockedScreen(error: String?, onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            IconBadge(Icons.Outlined.Lock, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, size = 96.dp)
            Spacer(Modifier.height(24.dp))
            Text("Hisaab is locked", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Use your fingerprint, face or screen lock to open your money on this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Unlock")
            }
            Text(
                "Everything stays on this device. No account, no internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

internal enum class Destination(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    ACTIVITY("Activity", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ACCOUNTS("Accounts", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    INSIGHTS("Insights", Icons.Filled.Insights, Icons.Outlined.Insights)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HisaabApp(store: FinanceStore, demoState: AppState? = null, onLock: () -> Unit) {
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
    fun startAdd(action: AddAction) {
        dialog = when (action) {
            AddAction.IMPORT -> { importStatement.launch(arrayOf("application/pdf")); null }
            AddAction.ACCOUNT -> "account"
            AddAction.CARD -> "card"
            AddAction.HOLDING -> "holding"
            AddAction.LOAN -> "loan"
            AddAction.BUDGET -> "budget"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    if (destination == Destination.HOME) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppMark()
                        Column {
                            Text(greeting(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Hisaab", style = MaterialTheme.typography.titleLarge)
                        }
                        if (demoState != null) Text(
                            "Demo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    } else Text(destination.label)
                },
                actions = {
                    if (demoState == null) IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, "Lock Hisaab now") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { destination = item },
                        icon = { Icon(if (item == destination) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (destination == Destination.HOME || destination == Destination.ACCOUNTS) ExtendedFloatingActionButton(
                onClick = { dialog = "add" },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        val screen = Modifier.padding(padding)
        val openTransaction: (Transaction) -> Unit = { dialog = "classify:${it.id}" }
        val openBudget: (String?) -> Unit = { category -> dialog = if (category == null) "budget" else "budget:$category" }
        when (destination) {
            Destination.HOME -> HomeScreen(state, screen, onGo = { destination = it }, onAdd = ::startAdd, onTransaction = openTransaction, onBudget = openBudget)
            Destination.ACTIVITY -> ActivityScreen(state, screen, onTransaction = openTransaction, onImport = { startAdd(AddAction.IMPORT) })
            Destination.ACCOUNTS -> AccountsScreen(state, screen, onAdd = ::startAdd, onEditHolding = { dialog = "holding:${it.id}" }, onRepay = { dialog = "repay:${it.id}" })
            Destination.INSIGHTS -> InsightsScreen(
                state, screen,
                onTransaction = openTransaction,
                onRemoveRule = { index -> update(state.copy(rules = state.rules.filterIndexed { itemIndex, _ -> itemIndex != index })) },
                onEditBudget = openBudget
            )
        }
    }

    val current = dialog
    val close = { dialog = null }
    when {
        current == "add" -> AddSheet(onDismiss = close) { action -> dialog = null; startAdd(action) }
        current == "account" -> AccountSheet(onDismiss = close) { name, type, amount ->
            update(state.copy(accounts = state.accounts + Account(UUID.randomUUID().toString(), name, type, balancePaise = amount)))
            dialog = null
        }
        current == "card" -> CardSheet(onDismiss = close) { name, ending, limit ->
            update(state.copy(cards = state.cards + CreditCard(UUID.randomUUID().toString(), name, ending, limit)))
            dialog = null
        }
        current == "loan" -> LoanSheet(onDismiss = close) { person, principal ->
            update(state.copy(loans = state.loans + Loan(UUID.randomUUID().toString(), person, principal)))
            dialog = null
        }
        current == "import" -> PasswordSheet(onDismiss = { pendingImport = null; dialog = null }) { password ->
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
        current != null && current.startsWith("classify:") -> {
            val transactionId = current.removePrefix("classify:")
            state.transactions.firstOrNull { it.id == transactionId }?.let { transaction ->
                val sourceName = state.accounts.firstOrNull { it.id == transaction.sourceId }?.name ?: state.cards.firstOrNull { it.id == transaction.sourceId }?.name
                TransactionSheet(transaction, sourceName, categoryOptions(state), onDismiss = close) { kind, category, keyword ->
                    val rule = keyword?.let { ClassificationRule(it, kind, category) }
                    update(state.copy(
                        transactions = state.transactions.map { if (it.id == transactionId) it.copy(kind = kind, category = category) else it },
                        rules = if (rule == null) state.rules else state.rules + rule
                    ))
                    dialog = null
                }
            }
        }
        current != null && current.startsWith("repay:") -> {
            state.loans.firstOrNull { it.id == current.removePrefix("repay:") }?.let { loan ->
                RepaySheet(loan, onDismiss = close) { amount ->
                    update(state.copy(loans = state.loans.map { if (it.id == loan.id) it.copy(repaidPaise = (it.repaidPaise + amount).coerceAtMost(it.principalPaise)) else it }))
                    dialog = null
                }
            }
        }
        current != null && (current == "budget" || current.startsWith("budget:")) -> {
            val category = current.removePrefix("budget").removePrefix(":")
            val existing = state.budgets.firstOrNull { it.category.equals(category, ignoreCase = true) }
            BudgetSheet(
                existing = existing,
                initialCategory = category,
                suggestions = budgetCategorySuggestions(state),
                onDismiss = close,
                onRemove = { update(state.copy(budgets = state.budgets.filterNot { it == existing })); dialog = null }
            ) { budget ->
                update(state.copy(budgets = state.budgets.filterNot { it.category.equals(budget.category, ignoreCase = true) } + budget))
                dialog = null
            }
        }
        current != null && (current == "holding" || current.startsWith("holding:")) -> {
            val existing = state.holdings.firstOrNull { it.id == current.removePrefix("holding:") }
            HoldingSheet(
                existing = existing,
                onDismiss = close,
                onRemove = { update(state.copy(holdings = state.holdings.filterNot { it.id == existing?.id })); dialog = null }
            ) { holding ->
                update(state.copy(holdings = state.holdings.filterNot { it.id == holding.id } + holding))
                dialog = null
            }
        }
    }
    importMessage?.let { message ->
        AlertDialog(onDismissRequest = { importMessage = null }, title = { Text("Statement imported") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { importMessage = null }) { Text("Done") } })
    }
    if (importing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Reading statement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("This happens on your phone. Nothing is uploaded.")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
    }
    vaultError?.let { error ->
        AlertDialog(onDismissRequest = {}, title = { Text("Vault needs attention") }, text = { Text("$error The app is read-only until this is resolved; it will not overwrite the vault.") }, confirmButton = { TextButton(onClick = {}) { Text("Keep read-only") } })
    }
}
