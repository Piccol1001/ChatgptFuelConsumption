package my.fuelledger

import android.app.Application
import my.fuelledger.data.*

class LedgerApplication : Application() {
    val repository by lazy { LedgerRepository(LedgerDatabase.open(this), SettingsStore(this)) }
}
