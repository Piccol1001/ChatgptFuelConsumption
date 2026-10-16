package my.fuelledger.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.*
import my.fuelledger.LedgerApplication
import my.fuelledger.MainActivity
import my.fuelledger.R
import my.fuelledger.data.*
import my.fuelledger.domain.Money
import java.time.*

/** Standard RemoteViews widgets: no services, network, cached plaintext amounts or new libraries. */
open class LedgerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onReceive(context: Context, intent: Intent) { super.onReceive(context, intent)
        if (intent.action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) refresh(context)
    }
    private fun refresh(context: Context) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { LedgerWidgets.update(context); (context.applicationContext as LedgerApplication).scheduleRecurring() }
            catch (_: Exception) { LedgerWidgets.unavailable(context) }
            finally { result.finish() }
        }
    }
}
class ExpenseSummaryWidget : LedgerWidgetProvider()
class QuickAddWidget : LedgerWidgetProvider()
class FinancialOverviewWidget : LedgerWidgetProvider()

data class WidgetSummary(val month: String, val expenses: Long, val income: Long, val budget: Long?, val hidden: Boolean) {
    fun money(value: Long) = if (hidden) "RM ••••" else Money.format(value)
    fun budgetLine() = budget?.let { if (hidden) "Budget usage hidden" else "${expenses * 100 / it}% used · ${Money.format(it - expenses)} remaining" } ?: "Monthly budget not configured"
}
fun widgetSummary(snapshot: Backup, month: YearMonth, zone: ZoneId): WidgetSummary = WidgetSummary(month.toString(),
    snapshot.expenses.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month }.sumOf { it.cents },
    financeMonthlyIncome(snapshot.incomes, month, zone), snapshot.budgets.find { it.month == month.toString() && it.categoryId.isEmpty() }?.cents,
    snapshot.settings.widgetPrivacy || snapshot.settings.privacyMode)

object LedgerWidgets {
    private val providers = listOf(ExpenseSummaryWidget::class.java, QuickAddWidget::class.java, FinancialOverviewWidget::class.java)
    private fun pending(context: Context, action: String): PendingIntent = PendingIntent.getActivity(context, action.hashCode(),
        Intent(context, MainActivity::class.java).apply { putExtra("ledger_action", action); flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = providers.associateWith { manager.getAppWidgetIds(ComponentName(context, it)) }
        if (ids.values.all { it.isEmpty() }) return
        val repository = (context.applicationContext as LedgerApplication).repository
        repository.initialize()
        val summary = widgetSummary(repository.backup(), YearMonth.now(), ZoneId.systemDefault())
        for ((provider, widgets) in ids) widgets.forEach { id ->
            val view = RemoteViews(context.packageName, R.layout.ledger_widget)
            view.setTextViewText(R.id.widget_title, when(provider) { ExpenseSummaryWidget::class.java -> "${summary.month} · Expenses"; QuickAddWidget::class.java -> "Quick add"; else -> "${summary.month} · Actual cash flow" })
            view.setTextViewText(R.id.widget_main, when(provider) { ExpenseSummaryWidget::class.java -> summary.money(summary.expenses); QuickAddWidget::class.java -> "Fuel & Expense Tracker"; else -> "Income ${summary.money(summary.income)}\nExpenses ${summary.money(summary.expenses)}" })
            view.setTextViewText(R.id.widget_detail, when(provider) { ExpenseSummaryWidget::class.java -> summary.budgetLine(); QuickAddWidget::class.java -> "Open a secure entry form"; else -> "Net ${summary.money(summary.income - summary.expenses)}\nCash flow · not a bank balance" })
            view.setOnClickPendingIntent(R.id.widget_root, pending(context, "Home"))
            view.setViewVisibility(R.id.widget_actions, if (provider == QuickAddWidget::class.java) android.view.View.VISIBLE else android.view.View.GONE)
            view.setOnClickPendingIntent(R.id.widget_expense, pending(context, "Expense"))
            view.setOnClickPendingIntent(R.id.widget_income, pending(context, "Income"))
            view.setOnClickPendingIntent(R.id.widget_scan, pending(context, "Scan"))
            manager.updateAppWidget(id, view)
        }
    }
    fun unavailable(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { provider -> manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
            val view = RemoteViews(context.packageName, R.layout.ledger_widget)
            view.setTextViewText(R.id.widget_title, "Fuel & Expense Tracker")
            view.setTextViewText(R.id.widget_main, "Open app to refresh")
            view.setTextViewText(R.id.widget_detail, "Local records remain protected")
            view.setOnClickPendingIntent(R.id.widget_root, pending(context, "Home"))
            manager.updateAppWidget(id, view)
        } }
    }
}
