package my.fuelledger.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import my.fuelledger.LedgerApplication
import my.fuelledger.MainActivity
import my.fuelledger.data.*
import my.fuelledger.testing.TestLedgerApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLedgerApplication::class, qualifiers = "w852dp-h393dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ClarityAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun nav(label: String) = compose.onNode(hasText(label) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun ready() = compose.waitUntil(30_000) { compose.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File("build/reports/clarity/adaptive").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun verify(theme: String, name: String, rail: Boolean) {
        ready()
        val app = compose.activity.application as LedgerApplication
        val repo = app.repository
        runBlocking(Dispatchers.IO) {
            repo.saveSettings(AppSettings(theme = theme))
            val now = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repo.saveExpense(Expense("clarity-food", 1250, "food", now, "Lunch"))
            repo.saveRefuel(Refuel("clarity-base", now - 86400000L * 6, 10000.0, 20.0, 4100, false, fuelType = "RON95"))
            repo.saveRefuel(Refuel("clarity-fill", now, 10400.0, 30.0, 6150, false, fuelType = "RON95"))
            app.financeRepository.saveIncome(FinanceIncome("clarity-income", 300000, now, "Salary"))
            app.financeRepository.saveBudget(FinanceBudget("clarity-budget", YearMonth.now().toString(), 120000))
        }
        compose.waitForIdle()
        for ((tab, title) in listOf("Home" to "Overview", "Expenses" to "Expenses", "Fuel" to "Fuel tracker", "Journey" to "Journey", "Analysis" to "Analytics")) {
            nav(tab).performClick(); compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
            nav(tab).assertIsSelected()
            val heading = compose.onNode(hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNode().boundsInRoot
            assertEquals("Shared heading gutter: $tab", if (rail) 120f else 16f, heading.left, 1f)
            val navigation = nav("Home").fetchSemanticsNode().boundsInRoot
            if (rail) assertTrue("Rail remains beside the page", navigation.right <= 104f)
            capture("$name-$tab")
        }
        nav("Expenses").performClick(); compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); nav("Fuel").assertIsSelected()
        nav("Expenses").performClick(); compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.onNode(hasText("Add Expense") and hasClickAction()).performClick()
        compose.onNodeWithText("Save").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Details & receipt").performScrollTo().performClick()
        val field = compose.onNode(hasText("Description (optional)") and hasSetTextAction())
        field.performScrollTo()
        assertTrue("Editor is bounded on wide displays", field.fetchSemanticsNode().boundsInRoot.width <= 528f)
    }
    @Test fun landscapeUsesRailAndRetainsPagerAndExpenseEntry() = verify("Dark", "852-land-dark", true)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletUsesRailWithoutStretchingPhoneControls() = verify("Light", "800-tablet-light", true)
    @Test @Config(qualifiers = "w480dp-h960dp-mdpi")
    fun largePhoneKeepsBottomNavigationAndSharedMargins() = verify("Light", "480-phone-light", false)
}
