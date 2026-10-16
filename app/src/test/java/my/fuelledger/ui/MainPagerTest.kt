package my.fuelledger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import my.fuelledger.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class, qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainPagerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val labels = listOf("Home", "Fuel", "Expenses", "Journey", "Analysis")
    private fun nav(label: String) = compose.onNode(hasText(label) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun settle() { compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle() }
    private fun ready(dark: Boolean = false) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Dashboard").fetchSemanticsNodes().isNotEmpty() }
        val repository = (compose.activity.application as LedgerApplication).repository
        runBlocking(Dispatchers.IO) { repository.saveSettings(AppSettings(theme = if (dark) "Dark" else "Light")) }
        settle()
    }
    private fun selected(label: String) {
        labels.forEach { if (it == label) nav(it).assertIsSelected() else nav(it).assertIsNotSelected() }
    }
    private fun navigate(label: String) { nav(label).performClick(); settle(); selected(label) }
    private fun swipe(left: Boolean) {
        compose.onNodeWithTag("main-pager").performTouchInput {
            swipe(Offset(width * if (left) .82f else .18f, 35f), Offset(width * if (left) .18f else .82f, 35f), 450)
        }; settle()
    }
    private fun list(page: String) = compose.onNode(hasScrollAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(hasTestTag("main-page-$page")))
    private fun scroll(page: String, matcher: SemanticsMatcher) { list(page).performScrollToNode(matcher) }
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; settle() }

    @Test fun swipesShowAdjacentPageSynchronizeSelectionRespectSlopAndStopAtBoundaries() {
        ready()
        swipe(false); selected("Home")
        compose.onNodeWithTag("main-pager").performTouchInput {
            swipe(Offset(width * .5f, 35f), Offset(width * .49f, 35f), 400)
        }; settle(); selected("Home")
        compose.onNodeWithTag("main-pager").performTouchInput {
            down(Offset(width * .82f, 35f)); moveTo(Offset(width * .25f, 35f), 450)
        }
        compose.onNodeWithTag("main-page-Home").assertIsDisplayed()
        compose.onNodeWithTag("main-page-Fuel").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").performTouchInput { up() }; settle(); selected("Fuel")
        for (label in labels.drop(2)) { swipe(true); selected(label) }
        swipe(true); selected("Analysis")
        for (label in labels.dropLast(1).reversed()) { swipe(false); selected(label) }
        for (label in labels) navigate(label)
        // Fast repeated taps converge on the final request, without competing navigation jobs.
        nav("Home").performClick(); nav("Journey").performClick(); nav("Fuel").performClick(); settle(); selected("Fuel")
    }

    @Test fun journeyDraftAndScrollSurviveDistantPagesAndSettingsWithSameViewModels() {
        ready(dark = true)
        val owner = compose.activity
        val ledger = ViewModelProvider(owner)[LedgerViewModel::class.java]
        val journey = ViewModelProvider(owner)[JourneyViewModel::class.java]
        val finance = ViewModelProvider(owner)[FinanceViewModel::class.java]
        navigate("Journey")
        field("Origin").performTextInput("Kuala Lumpur")
        field("Destination").performTextInput("Penang")
        field("Outbound distance (km)").performScrollTo().performTextInput("150")
        field("Fallback (L/100 km)").performScrollTo().performTextInput("7")
        scroll("Journey", hasText("Saved journeys"))
        val position = list("Journey").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(position > 0)
        navigate("Home"); navigate("Analysis"); navigate("Journey")
        compose.onNodeWithText("Saved journeys").assertIsDisplayed()
        assertEquals(position, list("Journey").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .5f)
        navigate("Home")
        scroll("Home", hasContentDescription("Settings")); compose.onNodeWithContentDescription("Settings").performClick(); settle()
        compose.onAllNodesWithTag("main-pager").assertCountEquals(0)
        back(); selected("Home"); navigate("Journey")
        compose.onNodeWithText("Saved journeys").assertIsDisplayed()
        scroll("Journey", hasText("Origin"))
        field("Origin").assertTextContains("Kuala Lumpur"); field("Destination").assertTextContains("Penang")
        field("Outbound distance (km)").performScrollTo().assertTextContains("150")
        field("Fallback (L/100 km)").performScrollTo().assertTextContains("7")
        assertSame(ledger, ViewModelProvider(owner)[LedgerViewModel::class.java])
        assertSame(journey, ViewModelProvider(owner)[JourneyViewModel::class.java])
        assertSame(finance, ViewModelProvider(owner)[FinanceViewModel::class.java])
        val snapshot = runBlocking(Dispatchers.IO) { (owner.application as LedgerApplication).repository.backup() }
        assertTrue(snapshot.journeys.isEmpty()); assertTrue(snapshot.expenses.isEmpty())
    }

    @Test fun childCategoryScrollAtBothEdgesToggleAndChartsDoNotNavigate() {
        ready()
        navigate("Expenses")
        scroll("Expenses", hasText("All") and isSelectable())
        // The horizontal row is the closest scrollable ancestor of the All chip.
        val row = compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
            and hasAnyAncestor(hasTestTag("main-page-Expenses")) and hasAnyDescendant(hasText("All") and isSelectable()))
        repeat(3) { row.performTouchInput { swipeLeft() }; settle(); selected("Expenses") }
        repeat(4) { row.performTouchInput { swipeRight() }; settle(); selected("Expenses") }
        compose.onNode(hasText("Food") and isSelectable()).performClick(); selected("Expenses")
        navigate("Home"); navigate("Expenses")
        compose.onNode(hasText("Food") and isSelectable()).assertIsSelected()
        navigate("Journey")
        compose.onNodeWithText("One Way").performTouchInput { swipeLeft() }; settle(); selected("Journey")
        // Drag the whole toggle's touch region from its left segment to the right segment.
        compose.onNodeWithText("One Way").performTouchInput {
            swipe(Offset(10f, centerY), Offset(width * 1.8f, centerY), 500)
        }; settle(); selected("Journey")
        compose.onNode(hasText("Round Trip") and isSelectable()).assertIsSelected()
        navigate("Fuel")
        scroll("Fuel", hasText("Consumption trend"))
        compose.onNodeWithText("Consumption trend").performTouchInput { swipeLeft() }; settle(); selected("Fuel")
        // Vertical list drags remain independent of horizontal paging.
        val before = list("Fuel").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        list("Fuel").performTouchInput { swipeUp() }; settle(); selected("Fuel")
        assertTrue(list("Fuel").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before)
    }

    @Test fun activityRecreationPreservesPageDraftScrollAndViewModel() {
        ready(dark = true)
        swipe(true); selected("Fuel"); swipe(false); selected("Home")
        navigate("Journey")
        field("Origin").performTextInput("Draft after rotation")
        val journey = ViewModelProvider(compose.activity)[JourneyViewModel::class.java]
        scroll("Journey", hasText("Saved journeys"))
        val position = list("Journey").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.activityRule.scenario.recreate(); settle()
        selected("Journey")
        compose.onNodeWithText("Saved journeys").assertIsDisplayed()
        assertEquals(position, list("Journey").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .5f)
        assertSame(journey, ViewModelProvider(compose.activity)[JourneyViewModel::class.java])
        scroll("Journey", hasText("Origin")); field("Origin").assertTextContains("Draft after rotation")
    }

    @Test fun formsScannerAndPlannerSliderKeepTheirExistingScopeAndBackBehavior() {
        ready(dark = true)
        scroll("Home", hasText("Add Expense")); compose.onNode(hasText("Add Expense") and hasClickAction()).performClick(); settle()
        compose.onNodeWithTag("expense-key-1").performClick()
        compose.onNode(isDialog()).performTouchInput { swipeLeft() }; settle(); selected("Home")
        compose.onNodeWithTag("expense-amount").assertTextEquals("RM 0.01")
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
        compose.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }; settle()
        compose.onNode(isDialog()).assertDoesNotExist(); selected("Home")
        scroll("Home", hasText("Scan Receipt")); compose.onNode(hasText("Scan Receipt") and hasClickAction()).performClick(); settle()
        compose.onAllNodesWithTag("main-pager").assertCountEquals(0)
        back(); selected("Home")
        scroll("Home", hasText("Scan Receipt")); compose.onNode(hasText("Scan Receipt") and hasClickAction()).performClick(); settle()
        navigate("Fuel"); compose.onNodeWithTag("main-pager").assertExists()
        navigate("Home")
        scroll("Home", hasContentDescription("Settings")); compose.onNodeWithContentDescription("Settings").performClick(); settle()
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Monthly Money Planner"))
        compose.onNodeWithText("Monthly Money Planner").performClick(); settle()
        compose.onNodeWithText("Monthly Allocation").performScrollTo().performClick(); settle()
        val slider = compose.onNodeWithContentDescription("Savings allocation").performScrollTo()
        val before = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        slider.performTouchInput { swipe(Offset(width * .2f, centerY), Offset(width * .65f, centerY), 500) }; settle()
        assertNotEquals(before, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        compose.onAllNodesWithTag("main-pager").assertCountEquals(0)
        back(); back(); selected("Home")
    }
}
