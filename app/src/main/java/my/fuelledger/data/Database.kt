package my.fuelledger.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import my.fuelledger.domain.FuelPoint
import my.fuelledger.domain.SpendingPoint

const val FUEL_CATEGORY = "fuel-transport"
const val OTHER_CATEGORY = "others"

@Serializable
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(@PrimaryKey val id: String, val name: String, val icon: String,
    val color: Long, val custom: Boolean = true)

@Serializable
@Entity(tableName = "refuels")
data class Refuel(@PrimaryKey val id: String, val timestamp: Long, val odometer: Double,
    val litres: Double, val cents: Long, val fullTank: Boolean, val station: String = "", val notes: String = "") {
    fun point() = FuelPoint(id, timestamp, odometer, litres, cents, fullTank)
    val pricePerLitre get() = cents / 100.0 / litres
}

@Serializable
@Entity(tableName = "expenses", foreignKeys = [
    ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Refuel::class, parentColumns = ["id"], childColumns = ["fuelId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("categoryId"), Index(value = ["fuelId"], unique = true), Index("timestamp")])
data class Expense(@PrimaryKey val id: String, val cents: Long, val categoryId: String,
    val timestamp: Long, val description: String = "", val notes: String = "",
    val payment: String = "Cash", val fuelId: String? = null) {
    fun point() = SpendingPoint(timestamp, cents, categoryId)
}

/** Crash-safe bridge: a database restore commits its settings intent with the records. */
@Entity(tableName = "pending_settings")
data class PendingSettings(@PrimaryKey val id: Int = 1, val payload: String)

val defaultCategories = listOf(
    Category("food", "Food & Drinks", "food", 0xFFE98A57, false),
    Category("groceries", "Groceries", "basket", 0xFF629C77, false),
    Category("entertainment", "Entertainment", "movie", 0xFF9B80C5, false),
    Category(FUEL_CATEGORY, "Fuel & Transport", "fuel", 0xFF449AAD, false),
    Category("shopping", "Shopping", "bag", 0xFFD877A1, false),
    Category("bills", "Bills", "bill", 0xFFBB9A48, false),
    Category(OTHER_CATEGORY, "Others", "other", 0xFF8B8C9B, false)
)
val paymentMethods = listOf("Cash", "Debit Card", "Credit Card", "E-wallet")
val categoryIcons = listOf("food", "basket", "movie", "fuel", "bag", "bill", "other", "health")
val categoryColors = listOf(0xFFE98A57L, 0xFF629C77L, 0xFF9B80C5L, 0xFF449AADL, 0xFFD877A1L, 0xFFBB9A48L, 0xFF8B8C9BL, 0xFFB65E67L)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM categories ORDER BY custom, name") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC, id") fun expenses(): Flow<List<Expense>>
    @Query("SELECT * FROM refuels ORDER BY timestamp DESC, id") fun refuels(): Flow<List<Refuel>>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM expenses ORDER BY timestamp, id") suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM refuels ORDER BY timestamp, id") suspend fun allRefuels(): List<Refuel>
    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun expense(id: String): Expense?
    @Query("SELECT * FROM expenses WHERE fuelId = :id") suspend fun linkedExpense(id: String): Expense?
    @Query("SELECT * FROM pending_settings WHERE id = 1") suspend fun pendingSettings(): PendingSettings?
    @Upsert suspend fun putCategory(category: Category)
    @Upsert suspend fun putExpense(expense: Expense)
    @Upsert suspend fun putRefuel(refuel: Refuel)
    @Upsert suspend fun putPendingSettings(settings: PendingSettings)
    @Query("DELETE FROM pending_settings") suspend fun clearPendingSettings()
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun deleteExpense(id: String)
    @Query("DELETE FROM refuels WHERE id = :id") suspend fun deleteRefuel(id: String)
    @Query("UPDATE expenses SET categoryId = :replacement WHERE categoryId = :id") suspend fun reassignCategory(id: String, replacement: String)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM refuels") suspend fun clearRefuels()
    @Query("DELETE FROM categories") suspend fun clearCategories()
}

@Database(entities = [Category::class, Expense::class, Refuel::class, PendingSettings::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, LedgerDatabase::class.java, "fuel-ledger.db").build()
    }
}
