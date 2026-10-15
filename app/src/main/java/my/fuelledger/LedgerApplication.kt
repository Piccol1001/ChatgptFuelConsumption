package my.fuelledger

import android.app.Application
import my.fuelledger.data.*
import my.fuelledger.security.LocalVault
import my.fuelledger.finance.RecurringWorker

open class LedgerApplication : Application() {
    val vault by lazy { createVault() }
    protected open fun createVault(): LocalVault = LocalVault(this)
    protected open fun createDatabase(): LedgerDatabase = LedgerDatabase.open(this)
    val repository by lazy { LedgerRepository(createDatabase(), SettingsStore(this, vault)) }
    val financeRepository by lazy { FinanceRepository(repository.database) }
    open fun scheduleRecurring() { RecurringWorker.schedule(this) }
}
