package my.fuelledger.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import my.fuelledger.LedgerApplication
import my.fuelledger.MainActivity
import my.fuelledger.data.Refuel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import my.fuelledger.testing.TestLedgerApplication
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class, qualifiers = "w393dp-h851dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForEditorToClose(title: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
    }

    private fun scrollScreenTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(matcher)
    }

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun navigate(destination: String) {
        compose.onNode(hasText(destination) and hasClickAction() and isSelectable()).performClick()
    }

    private fun captureScreen(name: String) {
        if (Build.VERSION.SDK_INT != 35) return
        runCatching {
            val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
            val appDirectory = if (File(workingDirectory, "src/main/AndroidManifest.xml").isFile) workingDirectory
                else File(workingDirectory, "app")
            val directory = File(appDirectory, "build/reports/ui-screenshots")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create screenshot directory." }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(directory, "$name-api35.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed." }
            }
        }.onFailure {
            System.err.println("Optional UI screenshot '$name' could not be saved: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    @Test fun launchAddEditAndNavigateTheRealComposeApp() {
        waitFor("Dashboard")
        scrollScreenTo(hasText("Add Expense"))
        compose.onNode(hasText("Add Expense") and hasClickAction()).performClick()
        field("Amount (RM)").performScrollTo().performTextInput("12.34")
        field("Description (optional)").performScrollTo().performTextInput("Coffee smoke test")
        compose.onNodeWithText("Save").performClick()
        waitForEditorToClose("Add expense")
        // Material's transient snackbar can cover the first history row on a tall phone.
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Expense saved").fetchSemanticsNodes().isEmpty() }
        scrollScreenTo(hasText("Coffee smoke test"))
        waitFor("Coffee smoke test")
        compose.onNodeWithText("Coffee smoke test").performClick()
        waitFor("Edit expense")
        field("Amount (RM)").performScrollTo().performTextReplacement("14.67")
        compose.onNodeWithText("Save").performClick()
        waitForEditorToClose("Edit expense")
        waitFor("RM 14.67")
        scrollScreenTo(hasText("Dashboard"))
        captureScreen("dashboard")

        navigate("Fuel")
        waitFor("Fuel tracker")
        captureScreen("fuel-tracker")
        compose.onAllNodesWithText("Trip cost estimator").assertCountEquals(0)
        navigate("Journey")
        waitFor("Journey Planner")
        field("One-way / outbound distance (km)").performScrollTo().performTextInput("150")
        // The documented 150 km, 7 L/100 km and RM2.05/L example rounds to RM21.53.
        scrollScreenTo(hasText("RM 21.53"))
        compose.onNodeWithText("RM 21.53").assertExists()

        navigate("Expenses")
        scrollScreenTo(hasText("Coffee smoke test"))
        compose.onNodeWithText("Coffee smoke test").assertExists()
        navigate("Analytics")
        waitFor("Understand your spending, at your own pace.")
        captureScreen("analytics")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Preferences")
        compose.onNodeWithText("Default petrol price (RM/L)").assertExists()
    }

    @Test fun journeySaveAndThemeChoiceUseExistingNavigationAndLocalStorage() {
        waitFor("Dashboard")
        navigate("Journey")
        waitFor("Journey Planner")
        field("Origin").performScrollTo().performTextInput("Kuala Lumpur")
        field("Destination").performScrollTo().performTextInput("Penang")
        field("One-way / outbound distance (km)").performScrollTo().performTextInput("150")
        scrollScreenTo(hasText("Save journey"))
        compose.onNodeWithText("Save journey").performClick()
        waitFor("Journey saved locally.")
        val repository = (compose.activity.application as LedgerApplication).repository
        val snapshot = runBlocking(Dispatchers.IO) { repository.backup() }
        val journeyVm = androidx.lifecycle.ViewModelProvider(compose.activity)[my.fuelledger.JourneyViewModel::class.java]
        org.junit.Assert.assertSame("Journey ViewModel must belong to the visible activity application", compose.activity.application, journeyVm.getApplication<android.app.Application>())
        assertEquals(1, snapshot.journeys.size)
        assertEquals("Penang", snapshot.journeys.single().plan().destination)
        assertTrue(snapshot.expenses.isEmpty()); assertTrue(snapshot.refuels.isEmpty())
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Preferences")
        scrollScreenTo(hasText("Theme"))
        compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { runBlocking(Dispatchers.IO) { repository.backup().settings.theme == "Dark" } }
        scrollScreenTo(hasText("Receipt Scanner"))
        compose.onNodeWithText("Save Mode").performClick()
        compose.onNodeWithText("Smart Auto-Save", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { runBlocking(Dispatchers.IO) { repository.backup().settings.receiptSaveMode == "Smart" } }
    }

    @Test fun partialRefuelShowsPerFillEstimateAndCreatesOneLinkedExpense() {
        waitFor("Dashboard")
        val repository = (compose.activity.application as LedgerApplication).repository
        val baselineId = "baseline-partial-refuel"
        runBlocking(Dispatchers.IO) {
            repository.saveRefuel(Refuel(baselineId, System.currentTimeMillis() - 86_400_000L,
                odometer = 10_000.0, litres = 10.0, cents = 2_050L, fullTank = false))
        }
        compose.waitForIdle()

        scrollScreenTo(hasText("Add Refuel"))
        compose.onNode(hasText("Add Refuel") and hasClickAction()).performClick()
        waitFor("Add refuel")
        field("Odometer (km)").performScrollTo().performTextInput("10500")
        field("Fuel quantity (L)").performScrollTo().performTextInput("40")
        field("Total payment (RM)").performScrollTo().performTextInput("82")

        compose.onNodeWithText("12.50 km/L").performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().assertIsOff()
        compose.onNodeWithText("Choose receipt photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        waitForEditorToClose("Add refuel")

        val snapshot = runBlocking(Dispatchers.IO) { repository.backup() }
        assertEquals(2, snapshot.refuels.size)
        val saved = snapshot.refuels.single { it.id != baselineId }
        assertEquals(10_500.0, saved.odometer, 0.0)
        assertEquals(40.0, saved.litres, 0.0)
        assertEquals(8_200L, saved.cents)
        assertFalse(saved.fullTank)
        assertNull(saved.receiptFile)
        assertEquals(2, snapshot.expenses.size)
        assertEquals(snapshot.refuels.map { it.id }.toSet(), snapshot.expenses.mapNotNull { it.fuelId }.toSet())
        assertEquals(1, snapshot.expenses.count { it.fuelId == saved.id })
        assertEquals(8_200L, snapshot.expenses.single { it.fuelId == saved.id }.cents)
        // Both partial fills appear once in spending; refuel records do not add a second charge.
        assertEquals(10_250L, snapshot.expenses.sumOf { it.cents })
    }
}
