package my.fuelledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import my.fuelledger.LedgerApplication
import my.fuelledger.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = LedgerApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun launchAddEditAndNavigateTheRealComposeApp() {
        waitFor("Your money, at a glance")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Expense"))
        compose.onNodeWithText("Expense", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Amount (RM)").performTextInput("12.34")
        compose.onNodeWithText("Description (optional)").performTextInput("Coffee smoke test")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Add expense").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Coffee smoke test"))
        waitFor("Coffee smoke test")
        compose.onNodeWithText("Coffee smoke test").performClick()
        waitFor("Edit expense")
        compose.onNodeWithText("Amount (RM)").performTextReplacement("14.67")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Edit expense").fetchSemanticsNodes().isEmpty() }
        waitFor("RM 14.67")
        compose.onNodeWithText("Fuel", useUnmergedTree = true).performClick()
        waitFor("Trip cost estimator")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("RM 21.53"))
        compose.onNodeWithText("RM 21.53").assertExists()
        compose.onNodeWithText("Analytics", useUnmergedTree = true).performClick()
        waitFor("Patterns worth seeing")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Preferences")
        compose.onNodeWithText("Default petrol price (RM/L)").assertExists()
    }
}
