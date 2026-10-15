package my.fuelledger.security

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** SQLCipher's modern Android helper, with an explicit preserve-on-corruption policy. */
internal class PreservingOpenHelperFactory(private val password: ByteArray) : SupportOpenHelperFactory(password) {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        object : SQLiteOpenHelper(configuration.context, configuration.name, password, null,
            configuration.callback.version, 0, DatabaseErrorHandler { _, error -> throw error }, null, false) {
            override fun onConfigure(db: SQLiteDatabase) = configuration.callback.onConfigure(db)
            override fun onCreate(db: SQLiteDatabase) = configuration.callback.onCreate(db)
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                configuration.callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                configuration.callback.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SQLiteDatabase) = configuration.callback.onOpen(db)
            override fun onBeforeDelete(db: SQLiteDatabase) {
                error("Database deletion is disabled; existing records were preserved.")
            }
        }
}
