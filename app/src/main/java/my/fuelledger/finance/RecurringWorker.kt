package my.fuelledger.finance

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import my.fuelledger.LedgerApplication
import my.fuelledger.data.FinanceRepository
import java.util.concurrent.TimeUnit

/** WorkManager persists this local schedule across process death and supported device reboots. */
class RecurringWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val ledger = (applicationContext as LedgerApplication).repository
        ledger.initialize()
        FinanceRepository(ledger.database).reconcile()
        PlannerReminders.update(applicationContext, ledger)
        my.fuelledger.widgets.LedgerWidgets.update(applicationContext)
        java.io.File(applicationContext.cacheDir, "local-exports").listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }.forEach { it.delete() }
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("local-recurring-expenses", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
