package my.fuelledger.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import my.fuelledger.LedgerState
import my.fuelledger.data.AppSettings
import my.fuelledger.data.Expense
import my.fuelledger.data.FUEL_CATEGORY
import my.fuelledger.data.Refuel
import my.fuelledger.data.defaultCategories
import java.time.LocalDate
import java.time.ZoneId

/** Sample-only state: previews never open Room, create a ViewModel, or modify saved data. */
private fun sampleLedger(): LedgerState {
    val today = LocalDate.now()
    fun time(daysAgo: Long) = today.minusDays(daysAgo).atTime(12, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val fills = listOf(
        Refuel("fill-3", time(0), 35680.0, 24.0, 4920, false, "PETRONAS", "", "RON 95", "Bangsar, Kuala Lumpur", "sample-receipt.jpg"),
        Refuel("fill-2", time(5), 35350.0, 29.0, 5945, true, "Shell", "Weekend drive", "RON 95", "Petaling Jaya"),
        Refuel("fill-1", time(12), 34940.0, 31.0, 6355, true, "PETRONAS", fuelType = "RON 95")
    )
    val fuelExpenses = fills.map { Expense("expense-${it.id}", it.cents, FUEL_CATEGORY, it.timestamp, "Refuel · ${it.station}", payment = "Credit Card", fuelId = it.id) }
    val expenses = listOf(
        Expense("coffee", 1850, "food", time(0) + 3_600_000, "Lunch & coffee", payment = "E-wallet"),
        Expense("groceries", 12680, "groceries", time(1), "Weekly groceries", payment = "Debit Card"),
        Expense("movie", 4200, "entertainment", time(3), "Movie night", payment = "E-wallet"),
        Expense("shopping", 18900, "shopping", time(6), "Running shoes", payment = "Credit Card"),
        Expense("internet", 13900, "bills", time(10), "Home internet", payment = "Debit Card")
    ) + fuelExpenses
    return LedgerState(defaultCategories, expenses.sortedByDescending { it.timestamp }, fills, AppSettings(theme = "Dark"))
}

@Composable
private fun PreviewLedger(dark: Boolean = true, content: @Composable (LedgerState) -> Unit) {
    LedgerTheme(dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) { content(sampleLedger()) }
        }
    }
}

@Preview(name = "Dashboard · dark", showBackground = true, widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDarkPreview() = PreviewLedger { HomeScreen(it, {}, {}, {}) }

@Preview(name = "Dashboard · light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeLightPreview() = PreviewLedger(dark = false) { HomeScreen(it, {}, {}, {}) }

@Preview(name = "Fuel tracker · dark", showBackground = true, widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FuelDarkPreview() = PreviewLedger { FuelScreen(it, {}, {}) }

@Preview(name = "Fuel tracker · light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FuelLightPreview() = PreviewLedger(dark = false) { FuelScreen(it, {}, {}) }

@Preview(name = "Expenses · dark", showBackground = true, widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpensesDarkPreview() = PreviewLedger { ExpensesScreen(it, {}, {}) }

@Preview(name = "Expenses · light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ExpensesLightPreview() = PreviewLedger(dark = false) { ExpensesScreen(it, {}, {}) }

@Preview(name = "Analytics · dark", showBackground = true, widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnalyticsDarkPreview() = PreviewLedger { AnalyticsScreen(it) }

@Preview(name = "Analytics · light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AnalyticsLightPreview() = PreviewLedger(dark = false) { AnalyticsScreen(it) }

@Preview(name = "Dashboard · large type / narrow phone", showBackground = true, widthDp = 320, heightDp = 900, fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccessibleHomePreview() = PreviewLedger { HomeScreen(it, {}, {}, {}) }

@Preview(name = "Empty dashboard", showBackground = true, widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyHomePreview() = PreviewLedger { HomeScreen(LedgerState(categories = defaultCategories), {}, {}, {}) }

@Preview(name = "Monthly Money Planner · dark", showBackground = true, widthDp = 393, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthlyPlannerDarkPreview() = PreviewLedger { LedgerCard { MonthlyPlanSummary(my.fuelledger.data.MonthlyMoney(300000, 113000, 300000, 138000, 25000)) } }

@Preview(name = "Monthly Money Planner · light / shortfall", showBackground = true, widthDp = 393, heightDp = 900)
@Composable
private fun MonthlyPlannerLightPreview() = PreviewLedger(dark = false) { LedgerCard { MonthlyPlanSummary(my.fuelledger.data.MonthlyMoney(100000, 113000, 0, 0, 0)) } }

@Preview(name = "Journey Planner estimate · dark", showBackground = true, widthDp = 393, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JourneyCostDarkPreview() = PreviewLedger { LedgerCard { JourneyCostSummary(my.fuelledger.data.journeyCost(my.fuelledger.data.JourneyPlan("KL", "Penang", 350.0, parkingCents = 500, people = 3))) } }

@Preview(name = "Journey Planner estimate · light", showBackground = true, widthDp = 393, heightDp = 400)
@Composable
private fun JourneyCostLightPreview() = PreviewLedger(dark = false) { LedgerCard { JourneyCostSummary(my.fuelledger.data.journeyCost(my.fuelledger.data.JourneyPlan("KL", "Ipoh", 200.0))) } }
