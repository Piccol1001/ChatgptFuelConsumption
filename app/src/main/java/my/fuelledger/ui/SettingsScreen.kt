package my.fuelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import my.fuelledger.*
import my.fuelledger.data.*
import java.util.UUID

@Composable
fun SettingsScreen(state: LedgerState, vm: LedgerViewModel, busy: Boolean, exportExpenses: () -> Unit,
    exportRefuels: () -> Unit, backup: () -> Unit, restore: () -> Unit) {
    var price by rememberSaveable(state.settings.petrolPrice) { mutableStateOf(state.settings.petrolPrice) }
    var consumption by rememberSaveable(state.settings.consumption) { mutableStateOf(state.settings.consumption.toString()) }
    var theme by rememberSaveable(state.settings.theme) { mutableStateOf(state.settings.theme) }
    var error by remember { mutableStateOf<String?>(null) }
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
                    Choice("Appearance", theme, listOf("System", "Light", "Dark").map { it to it }) { theme = it }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        runCatching { AppSettings(price.trim(), consumption.toDouble(), theme).also { it.validate() } }
                            .onSuccess { error = null; vm.saveSettings(it) {} }.onFailure { error = it.message ?: "Invalid preferences." }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save preferences") }
                }
            }
        }
        item { SectionTitle("Categories", "Built-in categories stay available. Custom categories can be changed.") }
        items(state.categories, key = { it.id }) { category ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(category)
                Column(Modifier.weight(1f)) { Text(category.name); Text(if (category.custom) "Custom" else "Built-in", style = MaterialTheme.typography.bodySmall) }
                if (category.custom) IconButton(onClick = { editCategory = category }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Edit ${category.name}") }
            }
        }
        item { OutlinedButton(onClick = { addCategory = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Outlined.Add, null); Text(" Create category") } }
        item { SectionTitle("Your data", "Use the system picker to choose a local folder. No storage permission is needed.") }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = exportExpenses, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export expenses to CSV") }
                    OutlinedButton(onClick = exportRefuels, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export refuels to CSV") }
                    Button(onClick = backup, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.SaveAlt, null); Text(" Create complete backup") }
                    OutlinedButton(onClick = restore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup…") }
                    Text("Backups include all transactions, categories and preferences. Restore validates the file and asks before replacing data. CSV files are for spreadsheets and cannot be restored.", style = MaterialTheme.typography.bodySmall)
                    Text("Backup files are readable by anyone who has the file. Store them in a private location.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Fuel & Expense Tracker · 1.0.0\nOffline by design. No account, ads or tracking.\nDates use your device's local time zone. Week totals run Monday to Sunday.\nBack up before uninstalling: Android removes the local database when you uninstall.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    if (confirm) DeleteConfirmation("Delete category?", "Existing expenses in this category will be moved to Others. No expense records will be deleted.", busy, { confirm = false }, delete)
}
