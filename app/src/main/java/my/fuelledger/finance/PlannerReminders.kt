package my.fuelledger.finance

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.first
import my.fuelledger.MainActivity
import my.fuelledger.R
import my.fuelledger.data.*
import java.time.*

/** Optional, on-device reminders. Lock-screen text contains no financial details. */
object PlannerReminders {
    private const val CHANNEL = "local-monthly-planning"
    suspend fun update(context: Context, ledger: LedgerRepository) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = ledger.preferences.settings.first()
        if (!settings.plannerReminders) { manager.cancel(2101); manager.cancel(2102); manager.cancel(2103); return }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Financial planning reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val today = LocalDate.now()
        val finance = FinanceRepository(ledger.database)
        val month = finance.ensureMonth(YearMonth.from(today))
        val next = finance.ensureMonth(YearMonth.from(today.plusDays(3)))
        val expenses = ledger.dao.allExpenses()
        val unpaid = (month.occurrences() + next.occurrences()).distinctBy { it.ruleId to it.date }.filter { due ->
            due.kind == "Expense" && LocalDate.parse(due.date) <= today.plusDays(3) &&
                expenses.none { it.recurringRuleId == due.ruleId && it.recurringDate == due.date }
        }
        val intent = PendingIntent.getActivity(context, 21, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun post(id: Int, title: String, text: String) {
            manager.notify(id, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.symbol_account_balance_wallet)
                .setContentTitle(title).setContentText(text).setContentIntent(intent).setAutoCancel(true)
                .setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
        }
        if (unpaid.isNotEmpty()) post(2101, "Review your fixed payments", "You have upcoming or unpaid commitments. Open your monthly planner to review.") else manager.cancel(2101)
        val budgets = ledger.database.financeDao().allBudgets().filter { it.month == YearMonth.from(today).toString() }
        if (budgets.any { financeBudgetSpending(it, expenses) >= it.cents * .9 }) post(2103, "Review your budgets", "A monthly budget has reached its alert threshold. Open the app for details.") else manager.cancel(2103)
        val money = monthlyMoney(YearMonth.from(today), month.occurrences(), emptyList(), emptyList())
        if (money.plannedAvailable < 0) post(2102, "Review your monthly plan", "Your fixed commitments exceed expected income. Open the app to adjust your plan.") else manager.cancel(2102)
    }
}
