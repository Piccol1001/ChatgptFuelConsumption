package my.fuelledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.domain.Money
import java.time.*
import kotlin.math.abs

@Serializable
@Entity(tableName = "expense_favourites")
data class FavouriteExpense(@PrimaryKey val id: String, val name: String, val payload: String) {
    fun expense(): Expense = backupJson.decodeFromString(payload)
}
fun favourite(id: String, name: String, expense: Expense) = FavouriteExpense(id, name, backupJson.encodeToString(
    expense.copy(fuelId = null, receiptFile = null, recurringRuleId = null, recurringDate = null, kind = "Expense", refundOf = null, cents = abs(expense.cents))))
    .also(::validateFavourite)
fun validateFavourite(value: FavouriteExpense) {
    require(value.id.isNotBlank() && value.id.length <= 100 && value.name.isNotBlank() && value.name.length <= 100) { "Enter a favourite name." }
    val expense = value.expense(); validateExpense(expense)
    require(expense.fuelId == null && expense.receiptFile == null && expense.recurringRuleId == null && expense.kind == "Expense" && expensePaymentGroup(expense.payment) != null) { "A favourite must be an ordinary expense template." }
}
@Serializable data class TransactionChange(val before: Expense, val after: Expense?, val beforeRefuel: Refuel? = null, val afterRefuel: Refuel? = null, val receiptScans: List<ReceiptScan> = emptyList())
@Entity(tableName = "transaction_undo")
data class TransactionUndo(@PrimaryKey val id: String, val timestamp: Long, val payload: String) {
    fun change(): TransactionChange = backupJson.decodeFromString(payload)
}
@Dao interface ExpenseToolsDao {
    @Query("SELECT * FROM expense_favourites ORDER BY name, id") fun favourites(): Flow<List<FavouriteExpense>>
    @Query("SELECT * FROM expense_favourites ORDER BY id") suspend fun allFavourites(): List<FavouriteExpense>
    @Upsert suspend fun putFavourite(value: FavouriteExpense)
    @Query("DELETE FROM expense_favourites WHERE id = :id") suspend fun deleteFavourite(id: String)
    @Query("DELETE FROM expense_favourites") suspend fun clearFavourites()
    @Query("SELECT * FROM transaction_undo ORDER BY timestamp DESC, id DESC") fun undoHistory(): Flow<List<TransactionUndo>>
    @Query("SELECT * FROM transaction_undo ORDER BY timestamp DESC, id DESC") suspend fun allUndo(): List<TransactionUndo>
    @Query("SELECT * FROM transaction_undo WHERE id = :id") suspend fun undo(id: String): TransactionUndo?
    @Upsert suspend fun putUndo(value: TransactionUndo)
    @Query("DELETE FROM transaction_undo WHERE id = :id") suspend fun deleteUndo(id: String)
    @Query("DELETE FROM transaction_undo") suspend fun clearUndo()
}

fun likelyDuplicates(candidate: Expense, records: List<Expense>): List<Expense> = records.filter { other ->
    other.id != candidate.id && other.kind == candidate.kind && other.cents == candidate.cents &&
        (candidate.receiptFile != null && candidate.receiptFile == other.receiptFile ||
         abs(other.timestamp - candidate.timestamp) <= 15 * 60_000 &&
             merchantKey(candidate.merchant.ifBlank { candidate.description }).isNotBlank() &&
             merchantKey(candidate.merchant.ifBlank { candidate.description }) == merchantKey(other.merchant.ifBlank { other.description }))
}
fun matchesExpense(expense: Expense, query: String, categories: List<Category>, zone: ZoneId): Boolean {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val haystack = listOf(expense.merchant, expense.description, expense.notes, expense.kind,
        categories.find { it.id == expense.categoryId }?.name.orEmpty(), expense.payment,
        expensePaymentGroup(expense.payment).orEmpty(), Money.decimal(abs(expense.cents)), Money.format(expense.cents),
        Instant.ofEpochMilli(expense.timestamp).atZone(zone).toLocalDate().toString()).joinToString(" ").lowercase()
    return tokens.all { it in haystack }
}
data class CalendarSummary(val total: Long, val highestDay: LocalDate?, val dailyAverageSen: Double)
fun calendarSummary(expenses: List<Expense>, month: YearMonth, zone: ZoneId, today: LocalDate): CalendarSummary {
    val days = expenses.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month }
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.mapValues { it.value.sumOf { e -> e.cents } }
    val elapsed = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
    return CalendarSummary(days.values.sum(), days.entries.filter { it.value > 0 }.maxByOrNull { it.value }?.key, days.values.sum().toDouble() / elapsed)
}
