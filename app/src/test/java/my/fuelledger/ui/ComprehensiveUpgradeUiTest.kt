package my.fuelledger.ui

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
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
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class, qualifiers = "w393dp-h851dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComprehensiveUpgradeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() { compose.waitUntil(30_000) { compose.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty() } }
    private fun settle() { compose.mainClock.advanceTimeBy(1000); compose.waitForIdle() }
    private fun repo() = (compose.activity.application as LedgerApplication).repository
    private fun nav(label: String) = compose.onNode(hasText(label) and isSelectable() and hasClickAction())
    private fun scroll(matcher: SemanticsMatcher) { compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(matcher) }
    private fun back() {
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } as? androidx.activity.ComponentDialog
        compose.runOnUiThread { if (dialog != null) dialog.onBackPressedDispatcher.onBackPressed() else compose.activity.onBackPressedDispatcher.onBackPressed() }; settle()
    }
    private fun waitDatabase(check: (Backup) -> Boolean) {
        val repository = repo()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[my.fuelledger.LedgerViewModel::class.java]
        compose.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.operationError.value?.let { throw AssertionError("Ledger operation failed: $it") }; !vm.busy.value && runBlocking(Dispatchers.IO) { check(repository.backup()) } }
        settle()
    }
    private fun keypad(digits: String) { compose.onNodeWithTag("expense-key-Clear").performScrollTo().performClick(); digits.forEach { compose.onNodeWithTag("expense-key-$it").performScrollTo().performClick() } }
    private fun actions(id: String) {
        compose.mainClock.advanceTimeBy(12_000); compose.waitForIdle()
        scroll(hasTestTag("transaction-$id"))
        compose.onNode(hasText("Actions") and hasClickAction() and hasAnyAncestor(hasTestTag("transaction-$id"))).performScrollTo().performClick()
    }
    @Test fun calendarOpensOutsidePagerAndCreatesExpenseOnSelectedLocalDate() {
        ready(); val repository = repo()
        compose.onNodeWithContentDescription("Calendar").performClick(); settle()
        compose.onNodeWithTag("main-pager").assertDoesNotExist()
        val selected = LocalDate.now().withDayOfMonth(1)
        compose.onNodeWithTag("calendar-date-$selected").performClick()
        scroll(hasText("Add for this date")); compose.onNodeWithText("Add for this date").performClick()
        keypad("1234"); compose.onNodeWithText("Save").performClick()
        waitDatabase { it.expenses.size == 1 }
        val snapshot = runBlocking(Dispatchers.IO) { repository.backup() }
        assertEquals(selected, localDate(snapshot.expenses.single().timestamp)); assertEquals(1234L, snapshot.expenses.single().cents)
        back(); nav("Home").assertIsSelected()
    }
    @Test fun refundFavouriteDeletionAndUndoUseExistingTransactionDatabase() {
        ready(); val repository = repo()
        val original = Expense("meal", 2000, "food", System.currentTimeMillis(), "Lunch", merchant = "Cafe")
        runBlocking(Dispatchers.IO) { repository.saveExpense(original); repository.saveFavourite(favourite("lunch", "Lunch", original)) }
        nav("Expenses").performClick(); settle()
        scroll(hasTestTag("favourite-lunch")); compose.onNodeWithTag("favourite-lunch").assertExists()
        actions("meal"); compose.onNodeWithText("Record refund").performClick(); keypad("500")
        compose.onNodeWithText("Save").performClick(); waitDatabase { it.expenses.size == 2 }
        val refund = runBlocking(Dispatchers.IO) { repository.backup().expenses.single { it.kind == "Refund" } }
        assertEquals(-500L, refund.cents); assertEquals("meal", refund.refundOf)
        actions(refund.id); compose.onNodeWithText("Delete").performClick()
        compose.onNode(hasText("Delete") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitDatabase { it.expenses.size == 1 }
        compose.mainClock.advanceTimeBy(12_000); compose.waitForIdle()
        scroll(hasTestTag("transaction-undo-action")); compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 180f) }; compose.onNodeWithTag("transaction-undo-action").performClick()
        waitDatabase { it.expenses.size == 2 }
        assertEquals(1500L, runBlocking(Dispatchers.IO) { repository.backup().expenses.sumOf { it.cents } })
    }
    @Test fun configuredTransactionSwipeOwnsGestureAndMainSwipeCanBeDisabled() {
        ready(); val repository = repo()
        runBlocking(Dispatchers.IO) { repository.saveExpense(Expense("swipe", 100, "food", System.currentTimeMillis(), "Swipe entry")); repository.saveSettings(AppSettings(mainSwipeEnabled = false)) }
        settle()
        compose.onNodeWithTag("main-pager").performTouchInput { swipe(Offset(width * .85f, 35f), Offset(width * .15f, 35f), 450) }; settle()
        nav("Home").assertIsSelected()
        nav("Expenses").performClick(); settle(); scroll(hasTestTag("transaction-swipe"))
        compose.onNodeWithTag("transaction-swipe").performTouchInput { swipe(Offset(width * .15f, height * .3f), Offset(width * .85f, height * .3f), 450) }; settle()
        compose.onNodeWithText("Edit expense").assertExists(); nav("Expenses").assertIsSelected(); compose.onNodeWithText("Cancel").performClick(); settle()
    }
    @Test fun widgetShortcutsWaitForOpenFormAndRouteToIncomeAndScanner() {
        ready()
        fun shortcut(value: String) { compose.runOnUiThread { compose.activity.acceptQuickAction(Intent(compose.activity, MainActivity::class.java).putExtra("ledger_action", value)) }; settle() }
        shortcut("Expense"); compose.onNodeWithText("Add expense").assertExists(); keypad("123")
        shortcut("Income"); compose.onNodeWithText("Add expense").assertExists()
        compose.onNodeWithText("Cancel").performClick(); settle()
        compose.onNodeWithText("Income amount (RM)").assertExists(); compose.onNodeWithText("Cancel").performClick(); settle()
        shortcut("Scan"); compose.onNodeWithText("Receipt Scanner").assertExists(); compose.onNodeWithTag("main-pager").assertDoesNotExist(); back()
        nav("Home").assertIsSelected()
    }
    @Test fun newRefundWithoutOriginalRemainsSeparateFromIncome() {
        ready(); val repository = repo()
        compose.onNodeWithContentDescription("Expense").performClick()
        keypad("750"); compose.onNodeWithText("Details & receipt").performScrollTo().performClick()
        compose.onNodeWithText("Refund").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick(); waitDatabase { it.expenses.size == 1 }
        val snapshot = runBlocking(Dispatchers.IO) { repository.backup() }
        val refund = snapshot.expenses.single()
        assertEquals("Refund", refund.kind); assertEquals(-750L, refund.cents); assertNull(refund.refundOf); assertTrue(snapshot.incomes.isEmpty())
    }

}
