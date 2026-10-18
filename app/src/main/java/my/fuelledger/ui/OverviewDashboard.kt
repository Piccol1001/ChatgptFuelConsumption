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
    val received = remember(finance.income, month, now.zone) { financeMonthlyIncome(finance.income, month, now.zone) }
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(LedgerSpacing.section)) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(now.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Malaysia)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Overview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                }
                if (settings != null) IconButton(settings) { Icon(ledgerSymbol("settings"), "Settings") }
            }
        }
        item(key = "today") {
            Surface(Modifier.fillMaxWidth().testTag("home-financial-card").clickable { openDay(now.today) }, color = DashboardColors.surface,
                contentColor = DashboardColors.primary, shape = RoundedCornerShape(LedgerSpacing.heroRadius)) {
                Column(Modifier.padding(LedgerSpacing.card), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's Expenses", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = DashboardColors.secondary)
                        IconButton(togglePrivacy, Modifier.size(LedgerSpacing.touchTarget)) {
                            Icon(if (state.settings.privacyMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Hide or reveal amounts", tint = DashboardColors.secondary)
                        }
                    }
                    Text(Money.format(totals.today(now.today)), Modifier.testTag("home-today-amount"), style = MaterialTheme.typography.headlineLarge)
                    HorizontalDivider(color = DashboardColors.divider)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("This Month", style = MaterialTheme.typography.bodySmall, color = DashboardColors.secondary)
                            Text(Money.format(spent), Modifier.testTag("home-month-amount"), style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Income Received", style = MaterialTheme.typography.bodySmall, color = DashboardColors.secondary)
                            Text(Money.format(received), Modifier.testTag("home-income-amount"), style = MaterialTheme.typography.titleMedium)
                        }
                    }
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
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple("Expense", Icons.Outlined.Add, expense), Triple("Fuel", Icons.Outlined.LocalGasStation, refuel)).forEach { (label, icon, action) ->
                    FilledTonalButton(action, Modifier.weight(1f).heightIn(min = 56.dp).fillMaxHeight(), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                        Icon(icon, label); Spacer(Modifier.width(8.dp)); Text("Add $label")
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
        FinanceState(income = listOf(FinanceIncome("sample-income", 300000, date, "Salary")), budgets = listOf(FinanceBudget("sample", month.toString(), 168000)), recurring = rules)
}
@Preview(name = "CLARITY Overview dark", widthDp = 393, heightDp = 852)
@Composable private fun OverviewReferenceDark() { val (s, f) = overviewSample(); LedgerTheme(true) { Surface { OverviewDashboard(s, f, {}, {}, {}, {}, {}, {}) } } }
@Preview(name = "CLARITY Overview light", widthDp = 393, heightDp = 852)
@Composable private fun OverviewReferenceLight() { val (s, f) = overviewSample(); LedgerTheme(false) { Surface { OverviewDashboard(s, f, {}, {}, {}, {}, {}, {}) } } }
