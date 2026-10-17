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
import androidx.compose.ui.platform.testTag
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
    restoreLegacy: () -> Unit = {}, openFinance: () -> Unit = {}, openRecurring: () -> Unit = openFinance) {
    var price by rememberSaveable(state.settings.petrolPrice) { mutableStateOf(state.settings.petrolPrice) }
    val historical = my.fuelledger.domain.historicalConsumption(state.refuels.map { it.point() })
    var consumption by rememberSaveable(state.settings.consumption) { mutableStateOf(state.settings.consumption.toString()) }
    var fallbackEnabled by rememberSaveable(state.settings.consumptionFallbackEnabled) { mutableStateOf(state.settings.consumptionFallbackEnabled) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.updateSettings({ it.copy(plannerReminders = true) })
    }
    var error by remember { mutableStateOf<String?>(null) }
    val receiptRules by vm.receiptMerchants.collectAsStateWithLifecycle()
    var merchantRule by remember { mutableStateOf<ReceiptMerchant?>(null) }
    var addMerchantRule by remember { mutableStateOf(false) }
    var addCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "planning") {
            LedgerCard {
                Text("Financial Planning", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(openFinance, Modifier.fillMaxWidth(), enabled = !busy) { Text("Monthly Money Planner") }
                TextButton(openRecurring, Modifier.fillMaxWidth(), enabled = !busy) { Text("Recurring Transactions") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Local reminders", Modifier.weight(1f))
                    Switch(state.settings.plannerReminders, enabled = !busy, onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.updateSettings({ it.copy(plannerReminders = enabled) })
                    })
                }
                ExpandableHelp("Reminder details") { Text("Upcoming bills and budget alerts are checked locally. Android can delay notifications; alerts hide financial details.", style = MaterialTheme.typography.bodySmall) }
                ExpandableHelp("Journey defaults") {
                    NumericField(price, { price = it }, "Default petrol price (RM/L)")
                    if (historical == null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Use a fallback without fuel history", Modifier.weight(1f)); Switch(fallbackEnabled, { fallbackEnabled = it })
                        }
                        if (fallbackEnabled) NumericField(consumption, { consumption = it }, "Fallback consumption (L/100 km)")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button({
                        runCatching { state.settings.copy(petrolPrice = price.trim(), consumption = if (historical == null && fallbackEnabled) consumption.toDouble() else state.settings.consumption, consumptionFallbackEnabled = fallbackEnabled).also { it.validate() } }
                            .onSuccess { value -> error = null; vm.updateSettings({ it.copy(petrolPrice = value.petrolPrice, consumption = value.consumption, consumptionFallbackEnabled = value.consumptionFallbackEnabled) }) }
                            .onFailure { error = it.message ?: "Invalid preferences." }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save fuel settings") }
                }
            }
        }
        item(key = "appearance") {
            LedgerCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Choice("Theme", state.settings.theme, listOf("System" to "System Default", "Light" to "Light", "Dark" to "Dark")) { value ->
                    if (!busy) vm.updateSettings({ it.copy(theme = value) })
                }
            }
        }
        item(key = "scanner") {
            LedgerCard {
                Text("Receipt Scanner", style = MaterialTheme.typography.titleMedium)
                Choice("Save Mode", state.settings.receiptSaveMode, listOf("Review" to "Review Before Saving", "Smart" to "Smart Auto-Save")) { value ->
                    if (!busy) vm.updateSettings({ it.copy(receiptSaveMode = value) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep receipt photos", Modifier.weight(1f))
                    Switch(state.settings.keepReceiptPhotos, { value -> vm.updateSettings({ it.copy(keepReceiptPhotos = value) }) }, enabled = !busy, modifier = Modifier.testTag("settings-keep-receipts"))
                }
                ExpandableHelp("Merchant rules (${receiptRules.size})") {
                    OutlinedButton({ addMerchantRule = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Add merchant rule") }
                    receiptRules.forEach { rule -> key(rule.merchant) {
                        OutlinedCard(onClick = { merchantRule = rule }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) { Text(rule.displayName); Text(state.categories.find { it.id == rule.categoryId }?.name ?: "Others", style = MaterialTheme.typography.bodySmall) }
                        }
                    } }
                }
                ExpandableHelp("Scanner safeguards") { Text("Recognition stays offline. Ambiguous values, duplicates and petrol receipts require review; forms always wait for Save.", style = MaterialTheme.typography.bodySmall) }
            }
        }
        item(key = "privacy") {
            PersonalizationSettings(state, vm, busy)
        }
        item(key = "lock") { AppLockSettings() }
        item(key = "categories") {
            LedgerCard {
                ExpandableHelp("Manage categories (${state.categories.size})") {
                    displayCategories(state.categories).forEach { category -> key(category.id) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryBadge(category)
                            Text(category.name, Modifier.weight(1f))
                            if (category.custom) IconButton({ editCategory = category }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Edit ${category.name}") }
                        }
                    } }
                    OutlinedButton({ addCategory = true }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Create category") }
                }
            }
        }
        item(key = "backup") {
            LedgerCard {
                Text("Backup & Export", style = MaterialTheme.typography.titleMedium)
                Button(backup, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Create encrypted backup") }
                OutlinedButton(restore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restore encrypted backup…") }
                ExpandableHelp("CSV & legacy import") {
                    OutlinedButton(exportExpenses, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export expenses to CSV") }
                    OutlinedButton(exportRefuels, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export refuels to CSV") }
                    OutlinedButton(restoreLegacy, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Import old JSON / ZIP…") }
                    Text("CSV and old JSON/ZIP are unencrypted. Restore replaces records after confirmation. Choose a private local destination.", style = MaterialTheme.typography.bodySmall)
                }
                ExpandableHelp("Backup details") { Text("Encrypted backups include retained photos. Passwords cannot be recovered. Back up before uninstalling; uninstalling deletes local records.", style = MaterialTheme.typography.bodySmall) }
                ExpandableHelp("Manage local storage") { LocalDataSettings(state, vm, busy) }
            }
        }
        item(key = "about") {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            LedgerCard {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("App Version", style = MaterialTheme.typography.labelLarge)
                Text(installed.versionName.orEmpty(), Modifier.testTag("installed-app-version"), style = MaterialTheme.typography.titleMedium)
                Text("Build ${if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()} · Offline · MYR", style = MaterialTheme.typography.bodySmall)
            }
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
