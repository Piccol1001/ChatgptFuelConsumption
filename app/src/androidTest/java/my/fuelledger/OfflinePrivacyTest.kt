package my.fuelledger

import android.content.pm.PackageManager
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import my.fuelledger.data.*
import my.fuelledger.domain.fillEstimates
import my.fuelledger.receipts.ReceiptScanner
import my.fuelledger.receipts.ReceiptStore
import my.fuelledger.security.LocalVault
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Run on a fresh installation with airplane mode enabled before the first app launch. */
@RunWith(AndroidJUnit4::class)
class OfflinePrivacyTest {
    @Test fun financialRecordsRecurringPaymentsAndEncryptedPhotoBackupWorkOffline() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        assertEquals(1, Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
        val directory = File(app.cacheDir, "offline-finance-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext() = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "no-backup").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        val databaseName = "offline-finance-${UUID.randomUUID()}.db"
        val vault = LocalVault(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var database: LedgerDatabase? = null
        try {
            val preferences = SettingsStore(context, vault, scope)
            database = LedgerDatabase.open(context, vault, databaseName)
            val ledger = LedgerRepository(database, preferences)
            ledger.initialize()
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            ledger.saveRefuel(Refuel("baseline", now - 86_400_000, 10000.0, 10.0, 2050, false))
            ledger.saveRefuel(Refuel("latest", now, 10500.0, 40.0, 8200, false))
            val receipts = ReceiptStore(context, vault)
            val bitmap = Bitmap.createBitmap(300, 150, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val photo = ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)); output.toByteArray()
            }
            bitmap.recycle()
            val receipt = receipts.saveRestored(photo)
            assertFalse(receipts.file(receipt).readBytes().take(2).toByteArray().contentEquals(photo.take(2).toByteArray()))
            ledger.saveExpense(Expense("food", 1234, "food", now, "Offline private expense", payment = "Card", receiptFile = receipt))
            ledger.saveSettings(AppSettings("2.77", 6.5, "Light", lastExpensePayment = "Card"))
            val finance = FinanceRepository(database)
            finance.saveIncome(FinanceIncome("salary", 250000, now, "Offline private salary"))
            finance.saveBudget(FinanceBudget("budget", YearMonth.from(today).toString(), 50000))
            finance.saveRecurring(FinanceRecurring("bill", "Offline recurring bill", 1000, "bills", "Monthly", today.toString(), today.toString()))
            assertEquals(0, finance.reconcile(today))
            val snapshot = ledger.backup()
            assertEquals(4, snapshot.expenses.size)
            assertEquals(12484L, snapshot.expenses.sumOf { it.cents })
            assertEquals(12.5, fillEstimates(snapshot.refuels.map { it.point() }).single().kmPerLitre, 0.00001)
            assertEquals(250000L, snapshot.incomes.single().cents)
            assertEquals(50000L, snapshot.budgets.single().cents)
            assertEquals(today.plusMonths(1).toString(), snapshot.recurring.single().nextDate)

            val archive = ReceiptBackupArchive(context, receipts, vault)
            val encrypted = ByteArrayOutputStream().also {
                archive.writeEncrypted(it, snapshot, "offline-test-password-2026".toCharArray())
            }.toByteArray()
            assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("Offline private salary"))
            archive.readEncrypted(ByteArrayInputStream(encrypted), "offline-test-password-2026".toCharArray()).use { preview ->
                assertEquals(snapshot, preview.backup)
                assertEquals(1, preview.receiptCount)
                val restored = archive.materialize(preview)
                ledger.restore(restored)
                assertArrayEquals(photo, receipts.readPhoto(restored.expenses.single { it.id == "food" }.receiptFile!!))
            }
            assertEquals(AppSettings("2.77", 6.5, "Light", lastExpensePayment = "Card"), preferences.settings.first())
            database.close()
            database = LedgerDatabase.open(context, vault, databaseName)
            assertEquals(4, database.dao().allExpenses().size)
            assertEquals("Card", database.dao().allExpenses().single { it.id == "food" }.payment)
            assertEquals(250000L, database.financeDao().allIncome().single().cents)
            assertEquals(0, FinanceRepository(database).reconcile(today))
            val updatedLedger = LedgerRepository(database, preferences)
            val planner = FinanceRepository(database)
            planner.saveRecurring(FinanceRecurring("expected", "Expected salary", 300000, "", "Monthly", today.withDayOfMonth(1).toString(), today.withDayOfMonth(1).toString(), kind = "Income", manualPayment = true))
            planner.saveRecurring(FinanceRecurring("fixed", "Fixed rent", 95000, "bills", "Monthly", today.withDayOfMonth(1).toString(), today.withDayOfMonth(1).toString(), manualPayment = true))
            planner.saveAllocation(allocationPlan("default", defaultAllocations))
            val due = planner.ensureMonth(YearMonth.from(today)).occurrences().single { it.ruleId == "fixed" }
            planner.markPaid(due, today, 95000)
            planner.markPaid(due, today, 95000)
            assertEquals(1, database.dao().allExpenses().count { it.recurringRuleId == "fixed" })
            JourneyRepository(database).save(savedJourney("journey", JourneyPlan("KL", "Penang", 350.0)))
            updatedLedger.saveSettings(AppSettings(theme = "System", receiptSaveMode = "Smart", lastExpensePayment = "E-wallet"))
            val scanned = my.fuelledger.receipts.parseReceipt("McDonald's\n$today\nGRAND TOTAL RM24.68")
            val expense = Expense("native-scan", 2468, "food", now, "McDonald's", payment = "Not specified")
            val fingerprint = receiptFingerprint(scanned.rawText)
            updatedLedger.saveReceiptExpense(expense, scanned, fingerprint, true, true)
            assertTrue(runCatching { updatedLedger.saveReceiptExpense(expense.copy(id = "duplicate-scan"), scanned, fingerprint, false, true) }.isFailure)
            val complete = updatedLedger.backup()
            archive.readEncrypted(ByteArrayInputStream(ByteArrayOutputStream().also {
                archive.writeEncrypted(it, complete, "Complete offline upgrade backup".toCharArray())
            }.toByteArray()), "Complete offline upgrade backup".toCharArray()).use { preview ->
                assertEquals(complete, preview.backup)
                updatedLedger.restore(archive.materialize(preview))
            }
            assertEquals(1, database.journeyDao().allJourneys().size)
            assertEquals(1, database.receiptAutomationDao().allScans().size)
            assertEquals(1, database.plannerDao().allAllocations().size)
            updatedLedger.undoReceiptExpense(expense)
            assertNull(database.dao().expense(expense.id))
            assertEquals("System", preferences.settings.first().theme)
            assertEquals("E-wallet", preferences.settings.first().lastExpensePayment)
            photo.fill(0); encrypted.fill(0)
        } finally {
            database?.close()
            scope.coroutineContext[Job]?.cancelAndJoin()
            context.deleteDatabase(databaseName)
            directory.deleteRecursively()
        }
    }

    @Test fun packagedApplicationHasNoNetworkOrPublicStoragePermission() {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        @Suppress("DEPRECATION")
        val permissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(permissions.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
        @Suppress("DEPRECATION")
        val services = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SERVICES)
            .services.orEmpty().map { it.name }
        assertFalse(services.any { it.contains("datatransport") || it.contains("firebase.analytics") })
        assertEquals(0, app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun bundledReceiptModelRecognizesTextWithoutAnyNetwork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        assertEquals("Enable airplane mode before fresh-install instrumentation", 1,
            Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
        val bitmap = Bitmap.createBitmap(1500, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 64f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        listOf("SHELL", "RON95 40.00 L", "TOTAL RM 82.00", "15/10/2026 10:30").forEachIndexed { index, text ->
            canvas.drawText(text, 80f, 140f + index * 150f, paint)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)); output.toByteArray()
        }
        bitmap.recycle()
        try {
            val result = withTimeout(90_000) { ReceiptScanner().scan(bytes) }
            assertEquals("82.00", result.amount)
            assertEquals(0, "40.00".toBigDecimal().compareTo(requireNotNull(result.litres).toBigDecimal()))
            assertEquals("RON95", result.fuelType)
            assertTrue(result.rawText.contains("SHELL"))
        } finally { bytes.fill(0) }
    }
}
