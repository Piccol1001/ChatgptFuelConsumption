package my.fuelledger.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
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
            finally { result?.finish() }
        }
    }
}
class ExpenseSummaryWidget : LedgerWidgetProvider()
class QuickAddWidget : LedgerWidgetProvider()
class FinancialOverviewWidget : LedgerWidgetProvider()

data class WidgetSummary(val month: String, val expenses: Long, val income: Long, val budget: Long?, val hidden: Boolean, val budgetSpent: Long = expenses, val budgetLabel: String = "Overall budget") {
    fun money(value: Long) = if (hidden) "RM ••••" else Money.format(value)
    fun budgetLine() = budget?.let { if (hidden) "Budget usage hidden" else "$budgetLabel · ${budgetSpent * 100 / it}% used · ${Money.format(it - budgetSpent)} remaining" } ?: "Monthly budget not configured"
}
fun widgetSummary(snapshot: Backup, month: YearMonth, zone: ZoneId): WidgetSummary {
    val expenses = snapshot.expenses.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month }
    val budgets = snapshot.budgets.filter { it.month == month.toString() }
    val overall = budgets.find { it.categoryId.isEmpty() }
    val budget = overall?.cents ?: budgets.takeIf { it.isNotEmpty() }?.sumOf { it.cents }
    val categoryIds = budgets.map { it.categoryId }.toSet()
    return WidgetSummary(month.toString(), expenses.sumOf { it.cents }, financeMonthlyIncome(snapshot.incomes, month, zone), budget,
        snapshot.settings.widgetPrivacy || snapshot.settings.privacyMode,
        if (overall != null) expenses.sumOf { it.cents } else expenses.filter { it.categoryId in categoryIds }.sumOf { it.cents },
        if (overall != null) "Overall budget" else "Category budgets")
}

object LedgerWidgets {
    // Preference writes, receiver refreshes and ledger commits can overlap. Serialize each
    // snapshot/render batch so a stale refresh cannot finish over a newer privacy state.
    private val updates = Mutex()
    private val providers = listOf(ExpenseSummaryWidget::class.java, QuickAddWidget::class.java, FinancialOverviewWidget::class.java)
    private fun pending(context: Context, action: String): PendingIntent = PendingIntent.getActivity(context, action.hashCode(),
        Intent(context, MainActivity::class.java).apply { putExtra("ledger_action", action); flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun update(context: Context) = updates.withLock { updateLocked(context) }
    private suspend fun updateLocked(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = providers.associateWith { manager.getAppWidgetIds(ComponentName(context, it)) }
        if (ids.values.all { it.isEmpty() }) return
        val repository = (context.applicationContext as LedgerApplication).repository
        repository.initialize()
        val month = YearMonth.now(); val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val summary = repository.database.withTransaction {
            val settings = repository.preferences.settings.first()
            val budgets = repository.database.financeDao().monthBudgets(month.toString())
            val overall = budgets.find { it.categoryId.isEmpty() }
            val total = repository.dao.sumExpenses(start, end)
            WidgetSummary(month.toString(), total, repository.database.financeDao().sumIncome(start, end),
                overall?.cents ?: budgets.takeIf { it.isNotEmpty() }?.sumOf { it.cents }, settings.widgetPrivacy || settings.privacyMode,
                if (overall != null) total else if (budgets.isNotEmpty()) repository.dao.sumCategoryExpenses(start, end, budgets.map { it.categoryId }) else 0,
                if (overall != null) "Overall budget" else "Category budgets")
        }
        for ((provider, widgets) in ids) widgets.forEach { id ->
            val view = RemoteViews(context.packageName, R.layout.ledger_widget)
            view.setTextViewText(R.id.widget_title, when(provider) { ExpenseSummaryWidget::class.java -> "${summary.month} · Expenses"; QuickAddWidget::class.java -> "Quick add"; else -> "${summary.month} · Actual cash flow" })
            view.setTextViewText(R.id.widget_main, when(provider) { ExpenseSummaryWidget::class.java -> summary.money(summary.expenses); QuickAddWidget::class.java -> "Fuel & Expense Tracker"; else -> "Income ${summary.money(summary.income)}\nExpenses ${summary.money(summary.expenses)}" })
            view.setViewVisibility(R.id.widget_main, if (provider == QuickAddWidget::class.java) android.view.View.GONE else android.view.View.VISIBLE)
            view.setTextViewText(R.id.widget_detail, when(provider) { ExpenseSummaryWidget::class.java -> summary.budgetLine(); QuickAddWidget::class.java -> "Open entry form"; else -> "Net ${summary.money(summary.income - summary.expenses)}\nCash flow · not a bank balance" })
            view.setOnClickPendingIntent(R.id.widget_root, pending(context, "Home"))
            view.setViewVisibility(R.id.widget_actions, if (provider == QuickAddWidget::class.java) android.view.View.VISIBLE else android.view.View.GONE)
            view.setOnClickPendingIntent(R.id.widget_expense, pending(context, "Expense"))
            view.setOnClickPendingIntent(R.id.widget_income, pending(context, "Income"))
            view.setOnClickPendingIntent(R.id.widget_scan, pending(context, "Scan"))
            manager.updateAppWidget(id, view)
        }
    }
    suspend fun unavailable(context: Context) = updates.withLock {
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
