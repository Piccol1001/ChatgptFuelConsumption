package my.fuelledger.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import my.fuelledger.LedgerViewModel
import my.fuelledger.data.*
import java.util.UUID

private val lightColors = lightColorScheme(primary = Color(0xFF006C60), onPrimary = Color.White,
    primaryContainer = Color(0xFFB5EEE0), secondary = Color(0xFFA45C3F), background = Color(0xFFF8FAF6),
    surface = Color(0xFFF8FAF6), surfaceContainer = Color(0xFFEBF1EB), surfaceContainerLow = Color(0xFFF0F5EF))
private val darkColors = darkColorScheme(primary = Color(0xFF83D5C2), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005145), secondary = Color(0xFFFFB596), background = Color(0xFF101714),
    surface = Color(0xFF101714), surfaceContainer = Color(0xFF202B25), surfaceContainerLow = Color(0xFF18211C))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(vm: LedgerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val startupError by vm.startupError.collectAsStateWithLifecycle()
    val restore by vm.restorePreview.collectAsStateWithLifecycle()
    val operationError by vm.operationError.collectAsStateWithLifecycle()
    val dark = when (state.settings.theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
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
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
        var tab by rememberSaveable { mutableStateOf("Home") }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
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
        BackHandler(settingsOpen) { settingsOpen = false }
        val csvExpenses = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { vm.export(it, "expenses") } }
        val csvRefuels = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { vm.export(it, "refuels") } }
        val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { vm.export(it, "backup") } }
        val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::inspectBackup) }
        val onExpense: (Expense) -> Unit = { expense ->
            if (expense.fuelId != null) refuelEditId = expense.fuelId
            else expenseEditId = expense.id
        }
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(if (settingsOpen) "Settings" else "Fuel & Expense") },
                    navigationIcon = { if (settingsOpen) IconButton(onClick = { settingsOpen = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    actions = { if (!settingsOpen) IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Outlined.Settings, "Settings") } })
            },
            bottomBar = { if (!settingsOpen) NavigationBar {
                listOf("Home" to Icons.Outlined.Home, "Fuel" to Icons.Outlined.LocalGasStation,
                    "Expenses" to Icons.Outlined.AccountBalanceWallet, "Analytics" to Icons.Outlined.BarChart).forEach { (title, icon) ->
                    NavigationBarItem(selected = tab == title, onClick = { tab = title }, icon = { Icon(icon, title) }, label = { Text(title) })
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
                } else if (settingsOpen) SettingsScreen(state, vm, busy,
                    { csvExpenses.launch("expenses.csv") }, { csvRefuels.launch("refuels.csv") },
                    { backup.launch("fuel-expense-backup.json") }, { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
                else Crossfade(tab, label = "Navigation") { destination ->
                    when (destination) {
                        "Fuel" -> FuelScreen(state, newRefuel, { refuelEditId = it.id })
                        "Expenses" -> ExpensesScreen(state, newExpense, onExpense)
                        "Analytics" -> AnalyticsScreen(state)
                        else -> HomeScreen(state, newExpense, newRefuel, onExpense)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        if (ready && state.categories.isNotEmpty() && (addExpense || expenseEditor != null)) {
            val seed = expenseEditor
            val id = seed?.id ?: newExpenseId
            ExpenseEditor(seed, id, state.categories, busy,
                dismiss = { if (!busy) { addExpense = false; expenseEditId = null } },
                save = { vm.saveExpense(it) { addExpense = false; expenseEditId = null } },
                delete = { vm.deleteExpense(id) { addExpense = false; expenseEditId = null } })
        }
        if (ready && state.categories.isNotEmpty() && (addRefuel || refuelEditor != null)) {
            val seed = refuelEditor
            val id = seed?.id ?: newRefuelId
            RefuelEditor(seed, id, busy, dismiss = { if (!busy) { addRefuel = false; refuelEditId = null } },
                save = { vm.saveRefuel(it) { addRefuel = false; refuelEditId = null } },
                delete = { vm.deleteRefuel(id) { addRefuel = false; refuelEditId = null } })
        }
        restore?.let { pending ->
            AlertDialog(onDismissRequest = { if (!busy) vm.cancelRestore() }, title = { Text("Replace data from backup?") },
                text = { Text("Validated backup: ${pending.expenses.size} expenses, ${pending.refuels.size} refuels and ${pending.categories.size} categories. This replaces all current records, categories and settings. Export a backup first if you want to keep your current data.") },
                confirmButton = { Button(onClick = vm::confirmRestore, enabled = !busy) { Text("Replace data") } },
                dismissButton = { TextButton(onClick = vm::cancelRestore, enabled = !busy) { Text("Cancel") } })
        }
        operationError?.let { message ->
            AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Could not complete the action") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } })
        }
    }
}
