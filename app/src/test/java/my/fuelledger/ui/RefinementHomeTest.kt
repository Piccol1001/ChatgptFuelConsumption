package my.fuelledger.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import my.fuelledger.*
import my.fuelledger.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefinementHomeTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 17)
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private fun time(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun verify(dark: Boolean) {
        val fuel = Refuel("f", time(today), 1200.0, 40.0, 8200, false)
        val state = mutableStateOf(LedgerState(defaultCategories, listOf(
            Expense("today", 1234, "food", time(today)), Expense("fuel", fuel.cents, FUEL_CATEGORY, fuel.timestamp, fuelId = fuel.id),
            Expense("refund", -200, "food", time(today), kind = "Refund"),
            Expense("yesterday", 999, "food", time(today.minusDays(1))), Expense("tomorrow", 500, "food", time(today.plusDays(1)))), listOf(fuel)))
        val clock = mutableStateOf(CalendarState(today, zone))
        val finance = mutableStateOf(FinanceState(income = listOf(FinanceIncome("income", 300000, time(today))),
            budgets = listOf(FinanceBudget("b", YearMonth.from(today).toString(), 10000))))
        var action = ""; var opened: LocalDate? = null
        compose.setContent { CompositionLocalProvider(LocalCalendarOverride provides clock.value) { LedgerTheme(dark) { Surface {
            OverviewDashboard(state.value, finance.value, { action = "Expense" }, { action = "Fuel" }, {}, { opened = it }, {}, {})
        } } } }
        compose.onNodeWithTag("home-today-amount", true).assertTextEquals("RM 92.34")
        compose.onNodeWithText("Available After Fixed Commitments").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expense").performClick(); assertEquals("Expense", action)
        compose.onNodeWithContentDescription("Fuel").performClick(); assertEquals("Fuel", action)
        compose.onNodeWithTag("home-today-amount", true).performClick(); assertEquals(today, opened)
        val layouts = mutableListOf<TextLayoutResult>()
        val label = compose.onNodeWithTag("home-budget-amount")
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(layouts.single().size.width.toFloat(), layouts.single().getLineRight(layouts.single().lineCount - 1), 1f)
        compose.onNodeWithTag("calendar-date-$today").performScrollTo()
        val cell = compose.onNodeWithTag("calendar-date-$today").fetchSemanticsNode().boundsInRoot
        val dayText = compose.onNode(hasText(today.dayOfMonth.toString()) and hasAnyAncestor(hasTestTag("calendar-date-$today")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Actual day text is centered inside its card", cell.center.x, dayText.center.x, .5f)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/reports/refinement").apply { mkdirs() }
        File(directory, "home-${if(dark) "dark" else "light"}.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        compose.runOnIdle { state.value = state.value.copy(expenses = state.value.expenses.map { if (it.id == "today") it.copy(cents = 2000) else it }) }
        compose.onNodeWithTag("home-today-amount", true).assertTextEquals("RM 100.00")
        compose.runOnIdle { state.value = state.value.copy(expenses = state.value.expenses.filterNot { it.id == "fuel" }) }
        // Leaving a refuel in this isolated UI fixture must not add its cost a second time.
        compose.onNodeWithTag("home-today-amount", true).assertTextEquals("RM 18.00")
        compose.runOnIdle { clock.value = clock.value.copy(today = today.plusDays(1)) }
        compose.onNodeWithTag("home-today-amount", true).assertTextEquals("RM 5.00")
        compose.runOnIdle { finance.value = finance.value.copy(budgets = listOf(FinanceBudget("b", YearMonth.from(today).toString(), 100000000000))) }
        layouts.clear(); label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(layouts.single().size.width.toFloat(), layouts.single().getLineRight(layouts.single().lineCount - 1), 1f)
    }
    @Test fun lightHomeUsesActualTodayReactsToEditsAndDateRollover() = verify(false)
    @Test @Config(qualifiers = "w320dp-h700dp-mdpi") fun narrowDarkHomeKeepsBudgetTrailingAlignmentAndTwoActions() = verify(true)
}
