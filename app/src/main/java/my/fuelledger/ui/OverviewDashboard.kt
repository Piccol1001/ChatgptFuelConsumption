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
fun OverviewDashboard(state: LedgerState, finance: FinanceState, expense: () -> Unit, income: () -> Unit,
    scan: () -> Unit, calendar: () -> Unit, settings: (() -> Unit)?, planner: () -> Unit, upcoming: () -> Unit, togglePrivacy: () -> Unit = {}) {
    val now = currentCalendar()
    val month = YearMonth.from(now.today)
    val plan = finance.months.find { it.month == month.toString() }?.occurrences() ?: plannedOccurrences(finance.recurring, month)
    val money = monthlyMoney(month, plan, finance.income, state.expenses, now.zone)
    val budget = finance.budgets.find { it.month == month.toString() && it.categoryId.isEmpty() }?.cents
    val due = plan.filter { it.kind == "Expense" && state.expenses.none { e -> e.recurringRuleId == it.ruleId && e.recurringDate == it.date } }
        .sortedBy { it.date }.take(4)
    val isDark = MaterialTheme.colorScheme.background.luminance() < .4f
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Malaysia)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Overview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                }
                IconButton(togglePrivacy) { Icon(if (state.settings.privacyMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Hide or reveal amounts") }
                if (settings != null) IconButton(settings) { Icon(ledgerSymbol("settings"), "Settings") }
            }
        }
        state.settings.dashboardOrder.filterNot { it in state.settings.dashboardHidden }.forEach { card -> when(card) {
            "plan" -> item {
            Surface(Modifier.fillMaxWidth().clickable(onClick = planner), color = if (isDark) Color(0xFF14283D) else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Available After Fixed Commitments", style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(money.plannedAvailable), style = MaterialTheme.typography.headlineLarge)
                    Text("Monthly Money Planner · Planned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Expected Income", style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(money.expected), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Fixed Commitments", style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(money.fixed), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        "budget" -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val usage = if (budget != null && budget > 0) money.actualExpenses.toDouble() / budget else null
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monthly Budget", style = MaterialTheme.typography.titleMedium)
                    Text(usage?.let { "${number(it * 100, 0)}%" } ?: "Not set", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(progress = { (usage ?: 0.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if ((usage ?: 0.0) >= 1) MaterialTheme.colorScheme.error else if ((usage ?: 0.0) >= .9) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${Money.format(money.actualExpenses)} spent", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(budget?.let { "${Money.format(it)} budget" } ?: "Configure in planner", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        "actions" -> item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Expense", Icons.Outlined.Add, expense), Triple("Income", Icons.Outlined.SouthWest, income),
                    Triple("Scan", Icons.Outlined.DocumentScanner, scan), Triple("Calendar", Icons.Outlined.CalendarMonth, calendar)).forEach { (label, icon, action) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalIconButton(action, Modifier.size(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(icon, label) }
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        "upcoming" -> item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Upcoming Payments", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(upcoming) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Recurring payments") }
            }
            if (due.isEmpty()) Text("No upcoming payments", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            due.forEach { item ->
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = upcoming), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryBadge(state.categories.find { it.id == item.categoryId })
                        Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleSmall); Text(dateLabel(LocalDate.parse(item.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(Money.format(item.cents), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        } }
    }
}

internal fun overviewSample(): Pair<LedgerState, FinanceState> {
    val month = YearMonth.now()
    val date = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val rules = listOf(FinanceRecurring("salary", "Salary", 300000, "", "Monthly", month.atDay(1).toString(), month.atDay(1).toString(), kind = "Income", manualPayment = true),
        FinanceRecurring("wifi", "Wi-Fi", 12000, "bills", "Monthly", month.atEndOfMonth().toString(), month.atEndOfMonth().toString(), manualPayment = true),
        FinanceRecurring("rent", "Rent", 101000, "bills", "Monthly", month.atDay(1).toString(), month.atDay(1).toString(), manualPayment = true))
    return LedgerState(categories = defaultCategories, expenses = listOf(Expense("sample", 25000, "food", date), Expense("sample-rent", 101000, "bills", date, recurringRuleId = "rent", recurringDate = month.atDay(1).toString()))) to
        FinanceState(budgets = listOf(FinanceBudget("sample", month.toString(), 168000)), recurring = rules)
}
@Preview(name = "Overview reference dark", widthDp = 393, heightDp = 851)
@Composable private fun OverviewReferenceDark() { val (s, f) = overviewSample(); LedgerTheme(true) { Surface { OverviewDashboard(s, f, {}, {}, {}, {}, {}, {}, {}) } } }
@Preview(name = "Overview reference light", widthDp = 393, heightDp = 851)
@Composable private fun OverviewReferenceLight() { val (s, f) = overviewSample(); LedgerTheme(false) { Surface { OverviewDashboard(s, f, {}, {}, {}, {}, {}, {}, {}) } } }
