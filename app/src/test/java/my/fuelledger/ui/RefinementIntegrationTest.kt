package my.fuelledger.ui

import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import my.fuelledger.*
import my.fuelledger.data.*
import my.fuelledger.testing.TestLedgerApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class, qualifiers = "w393dp-h851dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefinementIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var repository: LedgerRepository
    private lateinit var ledger: LedgerViewModel
    private fun ready() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty() }
        repository = (compose.activity.application as LedgerApplication).repository
        ledger = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
    }
    private fun repo() = repository
    private fun vm() = ledger
    private fun settle() { compose.mainClock.advanceTimeBy(1000); compose.waitForIdle() }
    private fun nav(label: String) = compose.onNode(hasText(label) and isSelectable() and hasClickAction())
    private fun scroll(matcher: SemanticsMatcher) = compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(matcher)
    private fun await(check: (Backup) -> Boolean) {
        val repository = repo()
        compose.waitUntil(15_000) { shadowOf(Looper.getMainLooper()).idle(); vm().operationError.value?.let { throw AssertionError(it) }; !vm().busy.value && runBlocking(Dispatchers.IO) { check(repository.backup()) } }
        settle()
    }
    @Test fun refuelEditsKeepHiddenMetadataOtherFuelTypesAndTimestampPrecision() {
        ready(); val original = Refuel("legacy", System.currentTimeMillis() - 86_400_000L, 12000.0, 42.75, 12345, true,
            "Legacy station", "  Original notes  ", "RON97", "Original station location")
        runBlocking(Dispatchers.IO) { repo().saveRefuel(original) }
        nav("Fuel").performClick(); settle()
        scroll(hasText("Legacy station")); compose.onNodeWithText("Legacy station").performClick(); settle()
        compose.onNodeWithText("Fuel type: RON97").assertExists()
        compose.onNodeWithText("Station location (optional)").assertDoesNotExist()
        compose.onNodeWithText("Notes (optional)").assertDoesNotExist()
        compose.onAllNodes(isToggleable() and hasAnyAncestor(isDialog())).assertCountEquals(0)
        compose.onNodeWithText("Save").performClick(); await { it.refuels.single() == original }
        val saved = runBlocking(Dispatchers.IO) { repo().backup() }
        assertEquals(original, saved.refuels.single())
        assertEquals(12345, saved.expenses.single().cents); assertEquals("legacy", saved.expenses.single().fuelId)
    }
    @Test fun manualExpenseChoicesAndPaymentOrderPreserveHistoricalFuelRecords() {
        ready(); val historical = Expense("old-fuel", 4100, FUEL_CATEGORY, System.currentTimeMillis(), "Old fuel transaction", payment = "Debit Card")
        runBlocking(Dispatchers.IO) { repo().saveExpense(historical) }
        compose.onNodeWithContentDescription("Expense").performClick(); settle()
        compose.onNodeWithContentDescription("Category Fuel & Transport").assertDoesNotExist()
        val payment = listOf("E-Wallet", "Cash", "Card").map { compose.onNodeWithContentDescription("Payment method $it").fetchSemanticsNode().boundsInRoot.center.x }
        assertTrue(payment.zipWithNext().all { it.first < it.second })
        compose.onNodeWithText("Cancel").performClick(); settle()
        nav("Expenses").performClick(); settle(); scroll(hasText("Transaction history"))
        compose.onNode(hasText("Fuel") and isSelectable() and hasAnyAncestor(hasTestTag("main-page-Expenses"))).performClick()
        scroll(hasText("Old fuel transaction")); compose.onNodeWithText("Old fuel transaction").performClick(); settle()
        compose.onNodeWithText("Existing category: Fuel & Transport").assertExists()
        compose.onNodeWithText("Save").performClick(); await { it.expenses.single() == historical }
        runBlocking(Dispatchers.IO) { repo().saveFavourite(favourite("old-fuel-template", "Old fuel template", historical)) }
        settle(); scroll(hasTestTag("favourite-old-fuel-template"))
        compose.onNodeWithTag("favourite-old-fuel-template").performClick(); settle()
        compose.onNodeWithText("Add refuel").assertExists()
        assertEquals(listOf(historical), runBlocking(Dispatchers.IO) { repo().backup().expenses })
        compose.onNodeWithText("Cancel").performClick()
    }
    @Test fun sharedEmbeddedCalendarShowsTheSameActualDailyRecordsOnBothScreens() {
        ready(); val today = LocalDate.now(); val stamp = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking(Dispatchers.IO) { repo().saveExpense(Expense("calendar", 850, "food", stamp, "Calendar meal")) }
        for (tab in listOf("Home", "Expenses")) {
            nav(tab).performClick(); settle(); scroll(hasTestTag("calendar-date-$today"))
            compose.onNodeWithTag("calendar-date-$today").performClick(); settle()
            scroll(hasText("${dateLabel(today)} · RM 8.50")); compose.onNodeWithText("${dateLabel(today)} · RM 8.50").assertIsDisplayed()
            compose.onNodeWithText("Calendar meal").assertExists()
            compose.onNodeWithTag("main-pager").assertExists(); nav(tab).assertIsSelected()
        }
    }
    @Test fun dailyChartOwnsItsHorizontalGestureAndPlanComparisonIsGone() {
        ready(); nav("Analysis").performClick(); settle(); scroll(hasText("Daily expenditure"))
        compose.onNodeWithTag("daily-chart").performTouchInput { swipeLeft() }; settle(); nav("Analysis").assertIsSelected()
        compose.onNodeWithTag("daily-chart-today").performClick(); settle()
        val today = compose.onNodeWithTag("daily-bar-${LocalDate.now()}").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("daily-chart").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.x, today.center.x, 1f)
        compose.onAllNodesWithText("Monthly plan vs actual", substring = true).assertCountEquals(0)
        scroll(hasText("Actual financial activity")); compose.onNodeWithText("Actual financial activity").assertIsDisplayed()
    }
    @Test fun settingsSwitchDoesNotBlinkUnrelatedButtonsAndAboutUsesInstalledVersion() {
        ready(); compose.onNodeWithContentDescription("Settings").performClick(); settle()
        scroll(hasText("Scanner safeguards")); compose.onNodeWithText("Scanner safeguards").assertIsEnabled()
        val before = compose.onNodeWithText("Scanner safeguards").captureToImage().asAndroidBitmap()
        val started = CountDownLatch(1); val gate = CountDownLatch(1)
        val repository = repo()
        val blocker = CoroutineScope(Dispatchers.IO).launch { repository.updateSettings { started.countDown(); check(gate.await(15, TimeUnit.SECONDS)); it } }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        try {
            compose.onNodeWithTag("settings-keep-receipts").performClick(); settle()
            assertFalse("A preference write must not globally disable the page", vm().busy.value)
            compose.onNodeWithText("Scanner safeguards").assertIsEnabled()
            val after = compose.onNodeWithText("Scanner safeguards").captureToImage().asAndroidBitmap()
            assertEquals(before.width, after.width); assertEquals(before.height, after.height)
            val a = IntArray(before.width * before.height); val b = IntArray(after.width * after.height)
            before.getPixels(a, 0, before.width, 0, 0, before.width, before.height); after.getPixels(b, 0, after.width, 0, 0, after.width, after.height)
            assertArrayEquals("Unrelated button pixels stay stable during a blocked preference write", a, b)
        } finally { gate.countDown(); runBlocking { blocker.join() } }
        await { !it.settings.keepReceiptPhotos }
        scroll(hasText("App Version"))
        compose.onNodeWithTag("installed-app-version").assertTextEquals(compose.activity.packageManager.getPackageInfo(compose.activity.packageName, 0).versionName.orEmpty())
        assertEquals(0, compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = java.io.File("build/reports/refinement").apply { mkdirs() }
        java.io.File(directory, "settings-about-api${android.os.Build.VERSION.SDK_INT}.png").outputStream().use { assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
