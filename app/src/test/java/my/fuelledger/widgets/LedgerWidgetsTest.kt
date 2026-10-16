package my.fuelledger.widgets

import android.appwidget.AppWidgetManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import my.fuelledger.LedgerApplication
import my.fuelledger.R
import my.fuelledger.data.*
import my.fuelledger.testing.TestLedgerApplication
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class)
class LedgerWidgetsTest {
    @Test fun widgetSummariesUseActualLocalMonthAndMaskByDefault() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val repo = app.repository; repo.initialize()
        val today = LocalDate.now(); val timestamp = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repo.saveExpense(Expense("expense", 1500, "food", timestamp))
        repo.database.financeDao().putIncome(FinanceIncome("income", 100000, timestamp))
        val summary = widgetSummary(repo.backup(), YearMonth.from(today), ZoneId.systemDefault())
        assertEquals(1500L, summary.expenses); assertEquals(100000L, summary.income); assertTrue(summary.hidden)
        assertEquals("RM ••••", summary.money(summary.expenses))
        repo.saveSettings(AppSettings(widgetPrivacy = false))
        assertEquals("RM 15.00", widgetSummary(repo.backup(), YearMonth.from(today), ZoneId.systemDefault()).money(1500))
    }
    @Test fun allThreeRealRemoteViewsInflateAndRefreshWithoutNetwork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val manager = shadowOf(AppWidgetManager.getInstance(app))
        val summary = manager.createWidget(ExpenseSummaryWidget::class.java, R.layout.ledger_widget)
        val quick = manager.createWidget(QuickAddWidget::class.java, R.layout.ledger_widget)
        val overview = manager.createWidget(FinancialOverviewWidget::class.java, R.layout.ledger_widget)
        withContextIo { LedgerWidgets.update(app) }
        assertEquals("RM ••••", manager.getViewFor(summary).findViewById<TextView>(R.id.widget_main).text.toString())
        assertEquals("Quick add", manager.getViewFor(quick).findViewById<TextView>(R.id.widget_title).text.toString())
        assertTrue(manager.getViewFor(overview).findViewById<TextView>(R.id.widget_detail).text.toString().contains("not a bank balance"))
    }
    private suspend fun withContextIo(block: suspend () -> Unit) = kotlinx.coroutines.withContext(Dispatchers.IO) { block() }
}
