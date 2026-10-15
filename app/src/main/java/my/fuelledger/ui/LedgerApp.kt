package my.fuelledger.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import my.fuelledger.LedgerViewModel
import my.fuelledger.FinanceViewModel
import my.fuelledger.data.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(vm: LedgerViewModel) {
    val locked = LocalAppLocked.current
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val startupError by vm.startupError.collectAsStateWithLifecycle()
    val restore by vm.restorePreview.collectAsStateWithLifecycle()
    val operationError by vm.operationError.collectAsStateWithLifecycle()
    val dark = when (state.settings.theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val financialViewModelFactory = remember(context.applicationContext) {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application)
    }
    val view = LocalView.current
    DisposableEffect(dark) {
        (context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        onDispose { }
    }
    MaterialTheme(colorScheme = if (dark) LedgerDarkColors else LedgerLightColors,
        typography = LedgerTypography, shapes = LedgerShapes) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
        var tab by rememberSaveable { mutableStateOf("Home") }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var scannerOpen by rememberSaveable { mutableStateOf(false) }
        var financeSection by rememberSaveable { mutableStateOf("Planner") }
        var financeOpen by rememberSaveable { mutableStateOf(false) }
        val openSettings = { scannerOpen = false; settingsOpen = true }
        var expenseEditId by rememberSaveable { mutableStateOf<String?>(null) }
        var refuelEditId by rememberSaveable { mutableStateOf<String?>(null) }
        val expenseEditor = state.expenses.firstOrNull { it.id == expenseEditId }
        val refuelEditor = state.refuels.firstOrNull { it.id == refuelEditId }
        var newExpenseId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
        var newRefuelId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
        var addExpense by rememberSaveable { mutableStateOf(false) }
        var addRefuel by rememberSaveable { mutableStateOf(false) }
        val newExpense: () -> Unit = { newExpenseId = UUID.randomUUID().toString(); addExpense = true }
        val newRefuel: () -> Unit = { newRefuelId = UUID.randomUUID().toString(); addRefuel = true }
        BackHandler((settingsOpen || financeOpen || scannerOpen) && !locked) { if (scannerOpen) scannerOpen = false else if (financeOpen) financeOpen = false else settingsOpen = false }
        // Picker callbacks can arrive while app lock is active. Keep only the URI until unlocked.
        var pendingCsv by remember { mutableStateOf<Pair<Uri, String>?>(null) }
        var pendingBackupOutput by remember { mutableStateOf<Uri?>(null) }
        var pendingRestoreInput by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
        val csvExpenses = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { pendingCsv = it to "expenses" } }
        val csvRefuels = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { pendingCsv = it to "refuels" } }
        val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { pendingBackupOutput = it } }
        val encryptedImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { pendingRestoreInput = it to false } }
        val legacyImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { pendingRestoreInput = it to true } }
        LaunchedEffect(locked, busy, pendingCsv, pendingRestoreInput) {
            if (!locked && !busy) {
                pendingCsv?.let { (uri, kind) -> pendingCsv = null; vm.export(uri, kind) }
                if (!vm.busy.value) pendingRestoreInput?.takeIf { it.second }?.let { (uri, _) ->
                    pendingRestoreInput = null
                    vm.previewRestore(uri, allowLegacy = true)
                }
            }
        }
        val onExpense: (Expense) -> Unit = { expense ->
            if (expense.fuelId != null) refuelEditId = expense.fuelId
            else expenseEditId = expense.id
        }
        Scaffold(
            topBar = {
                if (settingsOpen || financeOpen || scannerOpen) TopAppBar(title = {
                    if (scannerOpen) Text("Receipt Scanner")
                    else if (financeOpen) Text("Monthly Money Planner")
                    else Text("Settings")
                },
                    navigationIcon = { if (settingsOpen || financeOpen || scannerOpen) IconButton(onClick = { if (scannerOpen) scannerOpen = false else if (financeOpen) financeOpen = false else settingsOpen = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    actions = { if (!settingsOpen && !financeOpen) IconButton(onClick = openSettings) { Icon(ledgerSymbol("settings"), "Settings") } })
            },
            bottomBar = { if (!settingsOpen && !financeOpen) NavigationBar {
                listOf("Home" to "home", "Fuel" to "fuel", "Expenses" to "expenses", "Journey" to "route", "Analytics" to "analytics").forEach { (title, symbol) ->
                    NavigationBarItem(selected = tab == title, onClick = { scannerOpen = false; tab = title }, icon = { Icon(ledgerSymbol(symbol), title) }, label = { Text(title) })
                }
            } },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!ready || state.categories.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (startupError == null) { CircularProgressIndicator(); Text("Opening your local ledger…") }
                        else { Text(startupError!!); Button(onClick = vm::initialize, enabled = !busy) { Text("Retry") } }
                    }
                } else if (scannerOpen) { SmartReceiptScreen(state, vm) } else if (financeOpen) {
                    val financeVm: FinanceViewModel = viewModel(factory = financialViewModelFactory)
                    FinanceScreen(financeVm, initialSection = financeSection)
                } else if (settingsOpen) SettingsScreen(state, vm, busy,
                    { csvExpenses.launch("expenses.csv") }, { csvRefuels.launch("refuels.csv") },
                    { backup.launch("fuel-expense-backup.fuelbackup") },
                    { encryptedImport.launch(arrayOf("application/octet-stream", "*/*")) },
                    { legacyImport.launch(arrayOf("application/zip", "application/json", "text/*", "application/octet-stream")) },
                    { financeSection = "Expected Income"; financeOpen = true })
                else Crossfade(tab, label = "Navigation") { destination ->
                    when (destination) {
                        "Journey" -> { val journeyVm: my.fuelledger.JourneyViewModel = viewModel(factory = financialViewModelFactory); JourneyScreen(state.settings, journeyVm, state.refuels, saveFallback = { value -> vm.saveSettings(state.settings.copy(consumption = value, consumptionFallbackEnabled = true)) {} }, openSettings = openSettings) }
                        "Fuel" -> FuelScreen(state, newRefuel, { refuelEditId = it.id }, openSettings = openSettings)
                        "Expenses" -> ExpensesScreen(state, newExpense, onExpense, openSettings = openSettings)
                        "Analytics" -> { val financeVm: FinanceViewModel = viewModel(factory = financialViewModelFactory); val finance by financeVm.state.collectAsStateWithLifecycle(); AnalyticsScreen(state, finance, openSettings = openSettings) }
                        else -> {
                            val financeVm: FinanceViewModel = viewModel(factory = financialViewModelFactory)
                            val financeState by financeVm.state.collectAsStateWithLifecycle()
                            HomeScreen(state, newExpense, newRefuel, onExpense, planner = {
                                MonthlyPlanner(financeState, financeVm, compact = true, openDetails = { financeSection = "Planner"; financeOpen = true })
                            }, scanReceipt = { vm.clearReceiptWork(); vm.clearReceiptSuccess(); scannerOpen = true }, openSettings = openSettings)
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        if (ready && state.categories.isNotEmpty() && (addExpense || expenseEditor != null)) {
            val seed = expenseEditor
            val id = seed?.id ?: newExpenseId
            ExpenseEditor(seed, id, state.categories, busy, vm,
                dismiss = { if (!busy) { addExpense = false; expenseEditId = null } },
                save = { vm.saveExpense(it) { addExpense = false; expenseEditId = null } },
                delete = { vm.deleteExpense(id) { addExpense = false; expenseEditId = null } })
        }
        if (ready && state.categories.isNotEmpty() && (addRefuel || refuelEditor != null)) {
            val seed = refuelEditor
            val id = seed?.id ?: newRefuelId
            RefuelEditor(seed, id, busy, vm, dismiss = { if (!busy) { addRefuel = false; refuelEditId = null } },
                save = { vm.saveRefuel(it) { addRefuel = false; refuelEditId = null } },
                delete = { vm.deleteRefuel(id) { addRefuel = false; refuelEditId = null } })
        }
        if (!locked) pendingBackupOutput?.let { uri ->
            BackupPasswordDialog(creating = true, busy = busy,
                dismiss = { pendingBackupOutput = null }, submit = { password ->
                    pendingBackupOutput = null
                    vm.export(uri, "backup", password)
                })
        }
        if (!locked) pendingRestoreInput?.takeUnless { it.second }?.let { (uri, _) ->
            BackupPasswordDialog(creating = false, busy = busy,
                dismiss = { pendingRestoreInput = null }, submit = { password ->
                    pendingRestoreInput = null
                    vm.previewRestore(uri, password)
                })
        }
        if (!locked) restore?.let { pending ->
            AlertDialog(onDismissRequest = { if (!busy) vm.cancelRestore() }, title = { Text("Replace data from backup?") },
                text = { PrivacyDialogWindow(); Text("Validated backup: ${pending.backup.expenses.size} expenses, ${pending.backup.refuels.size} refuels, ${pending.backup.categories.size} categories, ${pending.receiptCount} receipt photos, ${pending.backup.incomes.size} income records, ${pending.backup.budgets.size} budgets and ${pending.backup.recurring.size} recurring rules, ${pending.backup.plannerMonths.size} monthly plans, ${pending.backup.allocations.size} allocation plans, ${pending.backup.journeys.size} journeys and ${pending.backup.receiptMerchants.size} merchant rules. This replaces all current records, categories and preferences. Create an encrypted backup first if you want to keep your current data.") },
                confirmButton = { Button(onClick = vm::confirmRestore, enabled = !busy) { Text("Replace data") } },
                dismissButton = { TextButton(onClick = vm::cancelRestore, enabled = !busy) { Text("Cancel") } },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
        }
        if (!locked) operationError?.let { message ->
            AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Could not complete the action") },
                text = { PrivacyDialogWindow(); Text(message) }, confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
        }
    }
}

/** Passwords are deliberately absent from saved instance state and are discarded on lock. */
@Composable
private fun BackupPasswordDialog(creating: Boolean, busy: Boolean, dismiss: () -> Unit, submit: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { password = ""; confirmation = "" } }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text(if (creating) "Encrypt your backup" else "Unlock backup") },
        text = {
            PrivacyDialogWindow()
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (creating) "Use 12–1024 characters. This password is never stored. Losing it can make the backup unrecoverable." else "Enter the password used when this encrypted backup was created.")
                OutlinedTextField(password, { if (it.length <= 1024) password = it }, label = { Text("Backup password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (creating) OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it }, label = { Text("Confirm backup password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Text("Choose a local folder. Cloud-synced folders and sharing with other apps are outside this app's local-storage protection.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = !busy, onClick = {
            if (password.isEmpty() || (creating && password.length < 12)) error = "Enter ${if (creating) "at least 12 characters" else "your backup password"}."
            else if (creating && password != confirmation) error = "The passwords do not match."
            else {
                val characters = password.toCharArray()
                password = ""; confirmation = ""
                submit(characters)
            }
        }) { Text(if (creating) "Encrypt & save" else "Validate backup") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy, securePolicy = SecureFlagPolicy.SecureOn))
}
