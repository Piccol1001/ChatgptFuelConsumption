package my.fuelledger.ui

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.fuelledger.*
import my.fuelledger.R
import my.fuelledger.data.*
import java.util.UUID

@Composable
fun SettingsScreen(state: LedgerState, vm: LedgerViewModel, busy: Boolean, exportExpenses: () -> Unit,
    exportRefuels: () -> Unit, backup: () -> Unit, restore: () -> Unit,
    restoreLegacy: () -> Unit = {}, openFinance: () -> Unit = {}, openAllocations: () -> Unit = openFinance) {
    var price by rememberSaveable(state.settings.petrolPrice) { mutableStateOf(state.settings.petrolPrice) }
    var consumption by rememberSaveable(state.settings.consumption) { mutableStateOf(state.settings.consumption.toString()) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.saveSettings(state.settings.copy(plannerReminders = true)) {}
    }
    var error by remember { mutableStateOf<String?>(null) }
    val receiptRules by vm.receiptMerchants.collectAsStateWithLifecycle()
    var merchantRule by remember { mutableStateOf<ReceiptMerchant?>(null) }
    var addMerchantRule by remember { mutableStateOf(false) }
    var addCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Make it yours", "MYR · kilometres · litres · English (Malaysia)") }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preferences", style = MaterialTheme.typography.titleMedium)
                    NumericField(price, { price = it }, "Default petrol price (RM/L)")
                    NumericField(consumption, { consumption = it }, "Estimated consumption (L/100 km)")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        runCatching { state.settings.copy(petrolPrice = price.trim(), consumption = consumption.toDouble()).also { it.validate() } }
                            .onSuccess { error = null; vm.saveSettings(it) {} }.onFailure { error = it.message ?: "Invalid preferences." }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save preferences") }
                }
            }
        }
        item { SectionTitle("Financial Planning", "Monthly Money Planner · Fixed Expenses · Monthly Allocation") }
        item {
            OutlinedButton(onClick = openFinance, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Icon(ledgerSymbol("wallet"), null); Spacer(Modifier.width(8.dp)); Text("Monthly Money Planner")
            }
        }
        item {
            OutlinedButton(onClick = openAllocations, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Monthly Allocation") }
        }
        item {
            LedgerCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Choice("Theme", state.settings.theme, listOf("System" to "System Default", "Light" to "Light", "Dark" to "Dark")) {
                    if (!busy) vm.saveSettings(state.settings.copy(theme = it)) {}
                }
                Text("Applies immediately and stays selected after reopening.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            LedgerCard {
                Text("Local planning reminders", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Upcoming, overdue and shortfall alerts", Modifier.weight(1f))
                    Switch(state.settings.plannerReminders, enabled = !busy, onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.saveSettings(state.settings.copy(plannerReminders = enabled)) {}
                    })
                }
                Text("Optional on-device checks when the app opens and about every 12 hours. Android may delay reminders; no exact alarm or internet service is used. Alerts hide financial details. Enable notifications in Android settings if previously blocked.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            LedgerCard {
                Text("Receipt Scanner", style = MaterialTheme.typography.titleMedium)
                Choice("Save Mode", state.settings.receiptSaveMode, listOf("Review" to "Review Before Saving", "Smart" to "Smart Auto-Save")) {
                    if (!busy) vm.saveSettings(state.settings.copy(receiptSaveMode = it)) {}
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep encrypted receipt photos by default", Modifier.weight(1f))
                    Switch(state.settings.keepReceiptPhotos, { vm.saveSettings(state.settings.copy(keepReceiptPhotos = it)) {} }, enabled = !busy)
                }
                Text("Use Scan Receipt on the dashboard. Smart mode saves only a known category, clear merchant, unique final total and valid date. Petrol receipts, foreign currencies and possible duplicates require review. Attachments inside an existing expense/refuel form always remain under your control.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ addMerchantRule = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Add merchant → category rule") }
            }
        }
        items(receiptRules, key = { "receipt-merchant-${it.merchant}" }) { rule ->
            OutlinedCard(onClick = { merchantRule = rule }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(rule.displayName)
                    Text(state.categories.find { it.id == rule.categoryId }?.name ?: "Others", style = MaterialTheme.typography.bodySmall)
                    Text("Tap to edit or remove", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { AppLockSettings() }
        item { SectionTitle("Categories", "Built-in categories stay available. Custom categories can be changed.") }
        items(state.categories, key = { it.id }) { category ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(category)
                Column(Modifier.weight(1f)) { Text(category.name); Text(if (category.custom) "Custom" else "Built-in", style = MaterialTheme.typography.bodySmall) }
                if (category.custom) IconButton(onClick = { editCategory = category }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Edit ${category.name}") }
            }
        }
        item { OutlinedButton(onClick = { addCategory = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Icon(ledgerSymbol("add"), null); Text(" Create category") } }
        item { SectionTitle("Your data", "Choose a local folder in the system picker. Password-protected backups include all records and retained receipt photos.") }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = exportExpenses, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export expenses to CSV") }
                    OutlinedButton(onClick = exportRefuels, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export refuels to CSV") }
                    Button(onClick = backup, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(ledgerSymbol("receipt"), null); Spacer(Modifier.width(8.dp)); Text("Create encrypted backup") }
                    OutlinedButton(onClick = restore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restore encrypted backup…") }
                    OutlinedButton(onClick = restoreLegacy, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Import old unencrypted JSON / ZIP…") }
                    Text("Backups include expenses, fuel records, income, budgets, recurring rules, categories, preferences and retained receipts. A password is required for new encrypted backups and is never stored. Losing it can make the backup unrecoverable.", style = MaterialTheme.typography.bodySmall)
                    Text("Old JSON/ZIP imports are unencrypted compatibility files. Restore validates a complete file and asks before replacing your records. CSV exports are unencrypted spreadsheet files and cannot be restored.", style = MaterialTheme.typography.bodySmall)
                    Text("Cloud-synced folders and sharing with another app are outside this app's local-storage protection. Choose a private local folder.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text(stringResource(R.string.app_display_name) + "\nOffline by design. No account, ads or tracking.\nDates use your device's local time zone. Week totals run Monday to Sunday.\nBack up before uninstalling: Android removes the local database when you uninstall.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (addMerchantRule || merchantRule != null) {
        val seed = merchantRule
        MerchantRuleEditor(seed, state.categories, busy, { addMerchantRule = false; merchantRule = null }, { name, category ->
            vm.saveReceiptMerchant(name, category) { addMerchantRule = false; merchantRule = null }
        }, { seed?.let { vm.deleteReceiptMerchant(it.merchant) }; addMerchantRule = false; merchantRule = null })
    }
    if (addCategory || editCategory != null) {
        val seed = editCategory
        val id = remember(seed?.id) { seed?.id ?: UUID.randomUUID().toString() }
        CategoryEditor(seed, id, busy, { if (!busy) { addCategory = false; editCategory = null } },
            { vm.saveCategory(it) { addCategory = false; editCategory = null } },
            { vm.deleteCategory(id) { addCategory = false; editCategory = null } })
    }
}
@Composable
private fun CategoryEditor(seed: Category?, id: String, busy: Boolean, dismiss: () -> Unit, save: (Category) -> Unit, delete: () -> Unit) {
    var name by rememberSaveable(id) { mutableStateOf(seed?.name.orEmpty()) }
    var icon by rememberSaveable(id) { mutableStateOf(seed?.icon ?: categoryIcons.first()) }
    var color by rememberSaveable(id) { mutableLongStateOf(seed?.color ?: categoryColors.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    EditorShell(if (seed == null) "Create category" else "Edit category", busy, error, dismiss, {
        runCatching { Category(id, name.trim(), icon, color).also(::validateCategory) }
            .onSuccess { error = null; save(it) }.onFailure { error = it.message }
    }, if (seed == null) null else ({ confirm = true })) {
        TextField(name, { name = it }, "Category name", 50)
        Choice("Icon", icon, categoryIcons.map { it to it.replaceFirstChar(Char::uppercase) }) { icon = it }
        Choice("Colour", color.toString(), categoryColors.zip(listOf("Orange", "Green", "Purple", "Blue", "Pink", "Gold", "Grey", "Red")).map { it.first.toString() to it.second }) { color = it.toLong() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CategoryBadge(Category(id, name, icon, color)); Text(name.ifBlank { "Category preview" }) }
    }
    if (confirm) DeleteConfirmation("Delete category?", "Expenses and recurring payments in this category move to Others. Its monthly budgets are combined with existing Others budgets. No expense records are deleted.", busy, { confirm = false }, delete)
}

@Composable
private fun MerchantRuleEditor(seed: ReceiptMerchant?, categories: List<Category>, busy: Boolean, close: () -> Unit,
    save: (String, String) -> Unit, delete: () -> Unit) {
    var name by rememberSaveable(seed?.merchant) { mutableStateOf(seed?.displayName.orEmpty()) }
    var category by rememberSaveable(seed?.merchant) { mutableStateOf(seed?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    EditorShell("Merchant category rule", busy, null, close, { save(name, category) }, if (seed != null) delete else null) {
        TextField(name, { if (seed == null) name = it }, "Merchant name", 200)
        Choice("Category", category, categories.map { it.id to it.name }) { category = it }
        Text("Merchant matching ignores punctuation and letter case. Remove an existing rule to change its merchant name.")
    }
}
