package my.fuelledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import my.fuelledger.FinanceState
import my.fuelledger.LedgerState
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun OverviewDashboard(state: LedgerState, finance: FinanceState, expense: () -> Unit, refuel: () -> Unit,
    edit: (Expense) -> Unit, openDay: (LocalDate) -> Unit, settings: (() -> Unit)?, upcoming: () -> Unit, togglePrivacy: () -> Unit = {}) {
    val now = currentCalendar()
    val month = YearMonth.from(now.today)
    val plan = remember(finance.months, finance.recurring, month) { finance.months.find { it.month == month.toString() }?.occurrences() ?: plannedOccurrences(finance.recurring, month) }
    val totals = remember(state.expenses, now.zone) { Spending(state.expenses.map { it.point() }, now.zone) }
    val spent = totals.month(month)
    val budget = finance.budgets.find { it.month == month.toString() && it.categoryId.isEmpty() }?.cents
    val due = remember(plan, state.expenses) { plan.filter { it.kind == "Expense" && state.expenses.none { e -> e.recurringRuleId == it.ruleId && e.recurringDate == it.date } }.sortedBy { it.date }.take(4) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < .4f
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Malaysia)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Overview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                }
                IconButton(togglePrivacy) { Icon(if (state.settings.privacyMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Hide or reveal amounts") }
                if (settings != null) IconButton(settings) { Icon(ledgerSymbol("settings"), "Settings") }
            }
        }
        item(key = "today") {
            Surface(Modifier.fillMaxWidth().clickable { openDay(now.today) }, color = if (isDark) Color(0xFF14283D) else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Today's Expenses", style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(totals.today(now.today)), Modifier.testTag("home-today-amount"), style = MaterialTheme.typography.headlineLarge)
                    Text(dateLabel(now.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "budget") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val usage = if (budget != null && budget > 0) spent.toDouble() / budget else null
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monthly Budget", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(usage?.let { "${number(it * 100, 0)}%${if (it > 1) " · Over budget" else ""}" } ?: "Not set", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(progress = { (usage ?: 0.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if ((usage ?: 0.0) > 1) MaterialTheme.colorScheme.error else if ((usage ?: 0.0) >= .9) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().testTag("home-budget-labels"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${Money.format(spent)} spent", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(budget?.let { "${Money.format(it)} budget" } ?: "Not set", Modifier.weight(1f).testTag("home-budget-amount"), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple("Expense", Icons.Outlined.Add, expense), Triple("Fuel", Icons.Outlined.LocalGasStation, refuel)).forEach { (label, icon, action) ->
                    FilledTonalButton(action, Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(icon, label); Spacer(Modifier.width(8.dp)); Text(label)
                    }
                }
            }
        }
        item(key = "calendar") { EmbeddedSpendingCalendar(state, edit, openDay) }
        item(key = "upcoming") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Upcoming Payments", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(upcoming) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Recurring payments") }
            }
            if (due.isEmpty()) Text("No upcoming payments", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            due.forEach { item ->
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = upcoming), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryBadge(state.categories.find { it.id == item.categoryId })
                        Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleSmall); Text("${if (LocalDate.parse(item.date) < now.today) "Overdue · " else "Due "}${dateLabel(LocalDate.parse(item.date))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(Money.format(item.cents), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

internal fun overviewSample(): Pair<LedgerState, FinanceState> {
    val month = YearMonth.now()
    val date = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val rules = listOf(FinanceRecurring("salary", "Salary", 300000, "", "Monthly", month.atDay(1).toString(), month.atDay(1).toString(), kind = "Income", manualPayment = true),
        FinanceRecurring("wifi", "Wi-Fi", 12000, "bills", "Monthly", month.atEndOfMonth().toString(), month.atEndOfMonth().toString(), manualPayment = true),
        FinanceRecurring("rent", "Rent", 101000, "bills", "Monthly", month.atDay(1).toString(), month.atDay(1).toString(), manualPayment = true))
    return LedgerState(categories = defaultCategories, expenses = listOf(Expense("sample", 25000, "food", LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()), Expense("sample-rent", 101000, "bills", date, recurringRuleId = "rent", recurringDate = month.atDay(1).toString()))) to
        FinanceState(budgets = listOf(FinanceBudget("sample", month.toString(), 168000)), recurring = rules)
}
@Preview(name = "Overview reference dark", widthDp = 393, heightDp = 851)
@Composable private fun OverviewReferenceDark() { val (s, f) = overviewSample(); LedgerTheme(true) { Surface { OverviewDashboard(s, f, {}, {}, {}, {}, {}, {}) } } }
@Preview(name = "Overview reference light", widthDp = 393, heightDp = 851)
@Composable private fun OverviewReferenceLight() { val (s, f) = overviewSample(); LedgerTheme(false) { Surface { OverviewDashboard(s, f, {}, {}, {}, {}, {}, {}) } } }
