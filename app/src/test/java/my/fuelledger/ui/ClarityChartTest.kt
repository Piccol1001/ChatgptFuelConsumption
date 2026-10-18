package my.fuelledger.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-mdpi")
class ClarityChartTest {
    @get:Rule val compose = createComposeRule()
    @Test fun refundDominatedExpensesRemainNegativeAndPlotOnTheOppositeSideOfIncome() {
        compose.setContent { LedgerTheme(false) { Surface {
            SpendingBars("Income vs Expenses", listOf("Actual income" to 50000L, "Net expenses" to -2500L))
        } } }
        compose.onNodeWithContentDescription("Net expenses: RM -25.00").assertExists()
        val positive = compose.onNodeWithTag("bar-positive-Actual income", true).fetchSemanticsNode().boundsInRoot
        val negative = compose.onNodeWithTag("bar-negative-Net expenses", true).fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithTag("bar-track-Income vs Expenses-Net expenses", true).fetchSemanticsNode().boundsInRoot
        assertEquals(track.center.x, positive.left, 1f)
        assertEquals(track.center.x, negative.right, 1f)
    }
}
