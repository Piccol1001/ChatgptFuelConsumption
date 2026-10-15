package my.fuelledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import my.fuelledger.domain.MAX_CENTS
import java.time.*
import java.time.temporal.ChronoUnit

@Serializable
@Entity(tableName = "finance_income")
data class FinanceIncome(@PrimaryKey val id: String, val cents: Long, val timestamp: Long,
    val source: String = "", val notes: String = "")

/** An empty category ID is the overall monthly spending budget. */
@Serializable
@Entity(tableName = "finance_budgets", indices = [Index(value = ["month", "categoryId"], unique = true)])
data class FinanceBudget(@PrimaryKey val id: String, val month: String, val cents: Long,
    val categoryId: String = "")

@Serializable
@Entity(tableName = "finance_recurring")
data class FinanceRecurring(@PrimaryKey val id: String, val name: String, val cents: Long,
    val categoryId: String, val frequency: String, val startDate: String, val nextDate: String,
    val enabled: Boolean = true, val payment: String = "Cash", val notes: String = "")

val financeFrequencies = listOf("Daily", "Weekly", "Monthly")

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_income ORDER BY timestamp DESC, id") fun income(): Flow<List<FinanceIncome>>
    @Query("SELECT * FROM finance_budgets ORDER BY month DESC, categoryId") fun budgets(): Flow<List<FinanceBudget>>
    @Query("SELECT * FROM finance_recurring ORDER BY name, id") fun recurring(): Flow<List<FinanceRecurring>>
    @Query("SELECT * FROM finance_income ORDER BY timestamp, id") suspend fun allIncome(): List<FinanceIncome>
    @Query("SELECT * FROM finance_budgets ORDER BY month, categoryId") suspend fun allBudgets(): List<FinanceBudget>
    @Query("SELECT * FROM finance_recurring ORDER BY id") suspend fun allRecurring(): List<FinanceRecurring>
    @Upsert suspend fun putIncome(value: FinanceIncome)
    @Upsert suspend fun putBudget(value: FinanceBudget)
    @Upsert suspend fun putRecurring(value: FinanceRecurring)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOccurrence(value: Expense): Long
    @Query("DELETE FROM finance_income WHERE id = :id") suspend fun deleteIncome(id: String)
    @Query("DELETE FROM finance_budgets WHERE id = :id") suspend fun deleteBudget(id: String)
    @Query("DELETE FROM finance_recurring WHERE id = :id") suspend fun deleteRecurring(id: String)
    @Query("UPDATE finance_recurring SET categoryId = :replacement WHERE categoryId = :oldId")
    suspend fun reassignRecurringCategory(oldId: String, replacement: String)
    @Query("DELETE FROM finance_income") suspend fun clearIncome()
    @Query("DELETE FROM finance_budgets") suspend fun clearBudgets()
    @Query("DELETE FROM finance_recurring") suspend fun clearRecurring()
}

private fun validateFinanceId(id: String) = require(id.isNotBlank() && id.length <= 64) { "Invalid financial record ID." }
fun validateFinanceIncome(value: FinanceIncome) {
    validateFinanceId(value.id)
    require(value.cents in 1..MAX_CENTS) { "Income must be a positive amount." }
    require(value.timestamp in 0..32_503_680_000_000L) { "Invalid income date." }
    require(value.source.length <= 100 && value.notes.length <= 2000) { "Income details are too long." }
}
fun validateFinanceBudget(value: FinanceBudget) {
    validateFinanceId(value.id)
    require(value.cents in 1..MAX_CENTS) { "Budget must be a positive amount." }
    val month = YearMonth.parse(value.month)
    require(month.year in 1970..2999 && month.toString() == value.month) { "Enter a valid budget month (YYYY-MM)." }
    require(value.categoryId.length <= 100) { "Invalid budget category." }
}
fun validateFinanceRecurring(value: FinanceRecurring) {
    validateFinanceId(value.id)
    require(value.name.isNotBlank() && value.name.length <= 200 && value.notes.length <= 2000) { "Enter a recurring expense name." }
    require(value.cents in 1..MAX_CENTS) { "Recurring payment must be a positive amount." }
    require(value.categoryId.isNotBlank() && value.categoryId.length <= 100) { "Choose a category." }
    require(value.frequency in financeFrequencies && value.payment in paymentMethods) { "Choose a valid frequency and payment method." }
    val start = LocalDate.parse(value.startDate)
    val next = LocalDate.parse(value.nextDate)
    require(start.year in 1970..2999 && next.year in 1970..3000 && next >= start) { "Invalid recurring expense dates." }
    require(start.toString() == value.startDate && next.toString() == value.nextDate) { "Use ISO dates for scheduled payments." }
    when (value.frequency) {
        "Weekly" -> require(ChronoUnit.DAYS.between(start, next) % 7 == 0L) { "The next weekly payment must keep its original weekday." }
        "Monthly" -> require(next.dayOfMonth == minOf(start.dayOfMonth, YearMonth.from(next).lengthOfMonth())) {
            "The next monthly payment must keep its original day."
        }
    }
}

/** Monthly rules retain their original day, including after shorter months. */
fun nextFinanceOccurrence(anchor: LocalDate, occurrence: LocalDate, frequency: String): LocalDate = when (frequency) {
    "Daily" -> occurrence.plusDays(1)
    "Weekly" -> occurrence.plusWeeks(1)
    "Monthly" -> YearMonth.from(occurrence).plusMonths(1).let { it.atDay(minOf(anchor.dayOfMonth, it.lengthOfMonth())) }
    else -> throw IllegalArgumentException("Unknown recurring frequency.")
}

fun financeMonthlyIncome(income: List<FinanceIncome>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Long =
    income.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month }.sumOf { it.cents }

fun financeBudgetSpending(budget: FinanceBudget, expenses: List<Expense>, zone: ZoneId = ZoneId.systemDefault()): Long =
    expenses.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)).toString() == budget.month &&
        (budget.categoryId.isEmpty() || it.categoryId == budget.categoryId) }.sumOf { it.cents }
