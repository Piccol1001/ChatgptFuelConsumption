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

    private fun enterExpenseAmount(digits: String) {
        compose.onNodeWithTag("expense-key-Clear").performScrollTo().performClick()
        digits.forEach { compose.onNodeWithTag("expense-key-$it").performScrollTo().performClick() }
    }

    private fun navigate(destination: String) {
        compose.onNode(hasText(destination) and hasClickAction() and isSelectable()).performClick()
    }

    private fun captureScreen(name: String, dialog: Boolean = false) {
        if (Build.VERSION.SDK_INT != 35) return
        runCatching {
            val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
            val appDirectory = if (File(workingDirectory, "src/main/AndroidManifest.xml").isFile) workingDirectory
                else File(workingDirectory, "app")
            val directory = File(appDirectory, "build/reports/ui-screenshots")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create screenshot directory." }
            val bitmap = (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap()
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
        enterExpenseAmount("1234")
        compose.onNodeWithText("Details & receipt").performScrollTo().performClick()
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
        enterExpenseAmount("1467")
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
        waitFor("Offline journey estimates")
        field("Outbound distance (km)").performScrollTo().performTextInput("150")
        field("Fallback (L/100 km)").performScrollTo().performTextInput("7")
        // The documented 150 km, 7 L/100 km and RM2.05/L example rounds to RM21.53.
        scrollScreenTo(hasText("RM 21.53"))
        compose.onNodeWithText("RM 21.53").assertExists()

        navigate("Expenses")
        scrollScreenTo(hasText("Coffee smoke test"))
        compose.onNodeWithText("Coffee smoke test").assertExists()
        navigate("Analysis")
        waitFor("Understand your spending, at your own pace.")
        captureScreen("analytics")
        scrollScreenTo(hasContentDescription("Settings"))
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Fuel settings")
        compose.onNodeWithText("Default petrol price (RM/L)").assertExists()
    }

    @Test fun journeySaveAndThemeChoiceUseExistingNavigationAndLocalStorage() {
        waitFor("Dashboard")
        navigate("Journey")
        waitFor("Offline journey estimates")
        field("Origin").performScrollTo().performTextInput("Kuala Lumpur")
        field("Destination").performScrollTo().performTextInput("Penang")
        field("Outbound distance (km)").performScrollTo().performTextInput("150")
        field("Fallback (L/100 km)").performScrollTo().performTextInput("7")
        scrollScreenTo(hasText("Save journey"))
        compose.onNodeWithText("Save journey").performClick()
        waitFor("Journey saved locally.")
        val repository = (compose.activity.application as LedgerApplication).repository
        val snapshot = runBlocking(Dispatchers.IO) { repository.backup() }
        val journeyVm = androidx.lifecycle.ViewModelProvider(compose.activity)[my.fuelledger.JourneyViewModel::class.java]
        org.junit.Assert.assertSame("Journey ViewModel must belong to the visible activity application", compose.activity.application, journeyVm.getApplication<android.app.Application>())
        assertEquals(1, snapshot.journeys.size)
        assertEquals("Penang", snapshot.journeys.single().plan().destination)
        assertEquals("Fallback", snapshot.journeys.single().plan().consumptionSource)
        assertTrue(snapshot.expenses.isEmpty()); assertTrue(snapshot.refuels.isEmpty())
        scrollScreenTo(hasContentDescription("Settings"))
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Fuel settings")
        scrollScreenTo(hasText("Theme"))
        compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { runBlocking(Dispatchers.IO) { repository.backup().settings.theme == "Dark" } }
        scrollScreenTo(hasText("Receipt Scanner"))
        compose.onNodeWithText("Save Mode").performClick()
        compose.onNodeWithText("Smart Auto-Save", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { runBlocking(Dispatchers.IO) { repository.backup().settings.receiptSaveMode == "Smart" } }
    }

    @Test fun historicalFuelUpdatesJourneyAutomaticallyAndAllowsExplicitOverride() {
        waitFor("Dashboard")
        val repository = (compose.activity.application as LedgerApplication).repository
        navigate("Journey")
        waitFor("Offline journey estimates")
        scrollScreenTo(hasText("Not enough fuel data"))
        compose.onNodeWithText("Not enough fuel data").assertExists()
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            repository.saveRefuel(Refuel("h1", now - 3 * 86400000L, 1000.0, 40.0, 8000, true))
            repository.saveRefuel(Refuel("hp", now - 2 * 86400000L, 1100.0, 10.0, 2000, false))
            repository.saveRefuel(Refuel("h2", now - 86400000L, 1200.0, 10.0, 2000, true))
        }
        waitFor("10.00 L/100 km")
        compose.onAllNodesWithText("Fallback (L/100 km)").assertCountEquals(0)
        runBlocking(Dispatchers.IO) { repository.saveRefuel(Refuel("h3", now, 1500.0, 15.0, 3000, true)) }
        waitFor("7.00 L/100 km")
        field("Origin").performScrollTo().performTextInput("KL")
        field("Destination").performScrollTo().performTextInput("Ipoh")
        field("Outbound distance (km)").performScrollTo().performTextInput("100")
        scrollScreenTo(hasText("Save journey"))
        compose.onNodeWithText("Save journey").performClick()
        waitFor("Journey saved locally.")
        assertEquals("History", runBlocking(Dispatchers.IO) { repository.backup().journeys.single().plan().consumptionSource })
        scrollScreenTo(hasText("Edit / load route"))
        compose.onNodeWithText("Edit / load route").performClick()
        scrollScreenTo(hasText("Override this journey"))
        compose.onNode(hasText("Override this journey").not() and isToggleable()).assertIsOff()
        runBlocking(Dispatchers.IO) { repository.saveRefuel(Refuel("h3", now, 1700.0, 15.0, 3000, true)) }
        waitFor("5.00 L/100 km")
        compose.onAllNodesWithText("Other journey fees").assertCountEquals(0)
        compose.onAllNodesWithText("Passenger cost sharing").assertCountEquals(0)
    }

    @Test fun expenseCategoryAndPaymentSaveExactlyAndLastPaymentIsReused() {
        waitFor("Dashboard")
        val repository = (compose.activity.application as LedgerApplication).repository
        val custom = my.fuelledger.data.Category("travel-custom", "Travel", "other", my.fuelledger.data.categoryColors.first())
        runBlocking(Dispatchers.IO) { repository.saveCategory(custom) }
        scrollScreenTo(hasText("Add Expense"))
        compose.onNode(hasText("Add Expense") and hasClickAction()).performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        enterExpenseAmount("00012345")
        compose.onNodeWithTag("expense-key-Backspace").performScrollTo().performClick()
        compose.onNodeWithTag("expense-amount").performScrollTo().assertTextEquals("RM 12.34")
        enterExpenseAmount("12345")
        compose.onNodeWithContentDescription("Category Travel").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Payment method E-Wallet").performScrollTo().performClick()
        captureScreen("add-expense", dialog = true)
        compose.waitUntil(10_000) { runBlocking(Dispatchers.IO) { repository.backup().settings.lastExpensePayment == "E-wallet" } }
        compose.onNodeWithText("Save").performClick()
        waitForEditorToClose("Add expense")
        val saved = runBlocking(Dispatchers.IO) { repository.backup().expenses.single() }
        assertEquals(12345L, saved.cents); assertEquals(custom.id, saved.categoryId); assertEquals("E-wallet", saved.payment)
        scrollScreenTo(hasText("Add Expense"))
        compose.onNode(hasText("Add Expense") and hasClickAction()).performClick()
        compose.onNodeWithTag("expense-amount").assertTextEquals("RM 0.00")
        compose.onNodeWithContentDescription("Payment method E-Wallet").assertIsSelected()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test fun legacyCardPaymentsAndExactTimestampsSurviveEditingAndUnknownNeedsExplicitChoice() {
        waitFor("Dashboard")
        val repository = (compose.activity.application as LedgerApplication).repository
        val now = System.currentTimeMillis()
        val legacy = listOf("Credit Card", "Debit Card", "Not specified").mapIndexed { index, payment ->
            my.fuelledger.data.Expense("legacy-$index", 12345L + index, "food", now - index * 1000, "Legacy $index", "Original notes", payment)
        }
        runBlocking(Dispatchers.IO) { legacy.forEach { repository.saveExpense(it) } }
        for (record in legacy.take(2)) {
            scrollScreenTo(hasText(record.description))
            compose.onNodeWithText(record.description).performClick()
            compose.onNodeWithTag("expense-amount").assertTextEquals(my.fuelledger.domain.Money.format(record.cents))
            compose.onNodeWithContentDescription("Payment method Card").performScrollTo().assertIsSelected().performClick()
            compose.onNodeWithText("Save").performClick()
            waitForEditorToClose("Edit expense")
            assertEquals(record, runBlocking(Dispatchers.IO) { repository.backup().expenses.first { it.id == record.id } })
            // Let the saved snackbar clear before tapping the next underlying history row.
            compose.mainClock.advanceTimeBy(6_000)
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Expense saved").fetchSemanticsNodes().isEmpty() }
        }
        val unknown = legacy.last()
        scrollScreenTo(hasText(unknown.description)); compose.onNodeWithText(unknown.description).performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        assertEquals(unknown, runBlocking(Dispatchers.IO) { repository.backup().expenses.first { it.id == unknown.id } })
        compose.onNodeWithContentDescription("Payment method Card").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick(); waitForEditorToClose("Edit expense")
        assertEquals(unknown.copy(payment = "Card"), runBlocking(Dispatchers.IO) { repository.backup().expenses.first { it.id == unknown.id } })
    }

    @Test fun collapsingDetailsRetainsAnUnsavedImportedReceiptUntilExpenseSave() {
        waitFor("Dashboard")
        val app = compose.activity.application as LedgerApplication
        val record = my.fuelledger.data.Expense("receipt-draft", 199L, "food", System.currentTimeMillis(), "Receipt draft")
        runBlocking(Dispatchers.IO) { app.repository.saveExpense(record) }
        scrollScreenTo(hasText(record.description)); compose.onNodeWithText(record.description).performClick()
        val photo = File(app.cacheDir, "expense-import.jpg")
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        photo.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }; bitmap.recycle()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[my.fuelledger.LedgerViewModel::class.java]
        org.junit.Assert.assertSame(app, vm.getApplication<android.app.Application>())
        compose.runOnIdle { vm.importReceipt(record.id, android.net.Uri.fromFile(photo)) }
        val receiptDirectory = File(app.filesDir, "receipts")
        try {
            compose.waitUntil(10_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.operationError.value?.let { throw AssertionError("Receipt import failed: $it") }
                !vm.busy.value && vm.receiptWork.value == null && receiptDirectory.listFiles().orEmpty().any { it.name.endsWith(".jpg.enc") }
            }
        } catch (failure: Throwable) {
            throw AssertionError("Receipt wait: busy=${vm.busy.value}, owner=${vm.receiptWork.value?.ownerId}, files=${receiptDirectory.listFiles().orEmpty().size}", failure)
        }
        val retained = receiptDirectory.listFiles()!!.single { it.name.endsWith(".jpg.enc") }
        compose.onNodeWithText("Details & receipt").performScrollTo().performClick()
        compose.onNodeWithText("Hide details & receipt").performScrollTo().performClick()
        compose.waitForIdle(); assertTrue(retained.isFile)
        compose.onNodeWithText("Save").performClick(); waitForEditorToClose("Edit expense")
        val saved = runBlocking(Dispatchers.IO) { app.repository.backup().expenses.single() }
        assertEquals(record.cents, saved.cents); assertEquals(retained.name.removeSuffix(".enc"), saved.receiptFile)
        assertTrue(my.fuelledger.receipts.ReceiptStore(app, app.vault).readPhoto(saved.receiptFile!!).isNotEmpty())
    }

    @Test fun unifiedPlannerKeepsReceivedIncomeAndAllocationSlidersSeparate() {
        waitFor("Dashboard")
        val repository = (compose.activity.application as LedgerApplication).repository
        val first = java.time.YearMonth.now().atDay(1).toString()
        runBlocking(Dispatchers.IO) {
            my.fuelledger.data.FinanceRepository(repository.database).saveRecurring(my.fuelledger.data.FinanceRecurring(
                "expected-salary", "Salary", 300000, "income:Salary", "Monthly", first, first, kind = "Income", manualPayment = true))
        }
        scrollScreenTo(hasContentDescription("Settings"))
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Fuel settings")
        compose.onAllNodesWithText("Monthly Allocation").assertCountEquals(0)
        scrollScreenTo(hasText("Monthly Money Planner"))
        compose.onNodeWithText("Monthly Money Planner").performClick()
        waitFor("Expected Income")
        compose.onNodeWithText("Received").performScrollTo().performClick()
        compose.onNodeWithText("Record received income").performScrollTo().performClick()
        waitFor("Add income")
        field("Income amount (RM)").performScrollTo().performTextInput("2500")
        compose.onNodeWithText("Save").performClick()
        waitForEditorToClose("Add income")
        compose.onNodeWithText("Monthly Allocation").performScrollTo().performClick()
        waitFor("Flexible remainder")
        compose.onNodeWithContentDescription("Savings allocation").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(3000f) }
        compose.onNodeWithText("Save allocations · 100%").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking(Dispatchers.IO) { repository.backup().allocations.any { it.scope == "default" } } }
        val snapshot = runBlocking(Dispatchers.IO) { repository.backup() }
        assertEquals(250000L, snapshot.incomes.single().cents)
        assertEquals(300000L, snapshot.recurring.single().cents)
        val allocations = snapshot.allocations.single { it.scope == "default" }.categories()
        assertEquals(3000, allocations.first { it.id == "savings" }.basisPoints)
        assertEquals(2500, allocations.first { it.id == "general" }.basisPoints)
        assertEquals(10000, allocations.sumOf { it.basisPoints })
        assertTrue(snapshot.expenses.isEmpty())
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
