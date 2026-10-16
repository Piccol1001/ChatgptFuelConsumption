package my.fuelledger.testing

import androidx.room.Room
import my.fuelledger.LedgerApplication
import my.fuelledger.data.LedgerDatabase
import my.fuelledger.security.LocalVault
import javax.crypto.spec.SecretKeySpec
import java.util.UUID

/** Host-only test seam. Production always opens SQLCipher with Android Keystore. */
class TestLedgerApplication : LedgerApplication() {
    override fun createVault() = LocalVault { SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES") }
    override fun createDatabase(): LedgerDatabase =
        Room.databaseBuilder(this, LedgerDatabase::class.java, "host-test-ledger-${UUID.randomUUID()}.db")
            .addMigrations(LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3, LedgerDatabase.MIGRATION_3_4, LedgerDatabase.MIGRATION_4_5).build()
    override fun scheduleRecurring() = Unit
}
