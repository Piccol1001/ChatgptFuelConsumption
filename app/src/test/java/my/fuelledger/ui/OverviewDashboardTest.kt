package my.fuelledger.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import my.fuelledger.data.monthlyMoney
import my.fuelledger.data.plannedOccurrences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverviewDashboardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun darkReferenceShowsTodayAndBudgetAndRenders() = reference(true)
    @Test fun lightReferenceShowsTodayAndBudgetAndRenders() = reference(false)
    private fun reference(dark: Boolean) {
        val (state, finance) = overviewSample()
        val money = monthlyMoney(YearMonth.now(), plannedOccurrences(finance.recurring, YearMonth.now()), emptyList(), state.expenses)
        assertEquals(187000, money.plannedAvailable)
        var action = ""
        compose.setContent { LedgerTheme(dark) { Surface { OverviewDashboard(state, finance, { action = "Expense" }, { action = "Fuel" }, {}, {}, {}, {}) } } }
        compose.onNodeWithTag("home-today-amount", useUnmergedTree = true).assertTextEquals(my.fuelledger.domain.Money.format(state.expenses.filter { localDate(it.timestamp) == java.time.LocalDate.now() }.sumOf { it.cents }))
        compose.onNodeWithText("75%").assertExists()
        compose.onNodeWithContentDescription("Fuel").performClick()
        assertEquals("Fuel", action)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/reports/comprehensive-upgrade").apply { mkdirs() }
        File(directory, "overview-${if(dark) "dark" else "light"}.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
