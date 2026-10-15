package my.fuelledger.receipts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import my.fuelledger.data.defaultCategories
import my.fuelledger.testing.TestLedgerApplication
import my.fuelledger.ui.ReceiptReviewDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLedgerApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptReviewTest {
    @get:Rule val compose = createComposeRule()

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    @Test fun reviewEditsOnlyCheckedValuesAndReturnsPrivateCopyDeletionChoice() {
        var reviewed: ReceiptSuggestions? = null
        var deletePhoto = false
        val suggestions = ReceiptSuggestions(amount = "12.00", station = "CAFE MAJU", date = "2026-10-15",
            time = "13:20", suggestedCategoryId = "food", rawText = "CAFE MAJU TOTAL RM12.00")
        compose.setContent { MaterialTheme {
            ReceiptReviewDialog(suggestions, defaultCategories, false, {}) { values, delete -> reviewed = values; deletePhoto = delete }
        } }
        assertNull(reviewed)
        field("Total payment (RM)").performScrollTo().performTextReplacement("14.50")
        compose.onNodeWithContentDescription("Use Time (HH:mm)").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Delete private copy after extraction").performScrollTo().performClick()
        compose.onNodeWithText("Apply selected").performClick()
        compose.runOnIdle {
            assertEquals("14.50", reviewed?.amount)
            assertEquals("CAFE MAJU", reviewed?.station)
            assertEquals("food", reviewed?.suggestedCategoryId)
            assertNull(reviewed?.time)
            assertTrue(deletePhoto)
        }
    }

    @Test fun invalidEditedDateBlocksApplyUntilTheUserCorrectsIt() {
        var reviewed: ReceiptSuggestions? = null
        compose.setContent { MaterialTheme {
            ReceiptReviewDialog(ReceiptSuggestions(amount = "20.00", date = "2026-10-15"), defaultCategories, false, {}) { values, _ -> reviewed = values }
        } }
        field("Date (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-02-31")
        compose.onNodeWithText("Apply selected").performClick()
        compose.onNodeWithText("Check the selected amounts, date, and time before applying.").performScrollTo().assertIsDisplayed()
        assertNull(reviewed)
        field("Date (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-02-28")
        compose.onNodeWithText("Apply selected").performClick()
        compose.runOnIdle { assertEquals("2026-02-28", reviewed?.date) }
    }
}
