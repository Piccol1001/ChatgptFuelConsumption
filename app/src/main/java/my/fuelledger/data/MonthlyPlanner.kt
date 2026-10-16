package my.fuelledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.*
import java.time.temporal.ChronoUnit

@Serializable
data class PlannedOccurrence(val ruleId: String, val date: String, val name: String, val cents: Long,
    val categoryId: String, val kind: String, val payment: String = "Cash")
@Serializable
@Entity(tableName = "finance_months")
data class FinanceMonth(@PrimaryKey val month: String, val payload: String) {
    fun occurrences(): List<PlannedOccurrence> = backupJson.decodeFromString(payload)
}
@Serializable
data class AllocationCategory(val id: String, val name: String, val basisPoints: Int,
    val categoryIds: List<String> = emptyList(), val savings: Boolean = false, val remainder: Boolean = false)
@Serializable
@Entity(tableName = "finance_allocations")
data class AllocationPlan(@PrimaryKey val scope: String, val payload: String) {
    fun categories(): List<AllocationCategory> = backupJson.decodeFromString(payload)
}
val defaultAllocations = listOf(
    AllocationCategory("savings", "Savings", 2000, savings = true),
    AllocationCategory("food", "Food & Groceries", 3000, listOf("food", "groceries")),
    AllocationCategory("lifestyle", "Entertainment & Lifestyle", 1500, listOf("entertainment", "shopping")),
    AllocationCategory("general", "General Spending", 3500, listOf(FUEL_CATEGORY, "bills", OTHER_CATEGORY)))
fun allocationPlan(scope: String, categories: List<AllocationCategory>) =
    AllocationPlan(scope, backupJson.encodeToString(categories)).also(::validateAllocationPlan)
fun validateAllocationPlan(plan: AllocationPlan) {
    require(plan.scope == "default" || validPlannerMonth(plan.scope)) { "Invalid allocation month." }
    val categories = plan.categories()
    require(categories.size in 1..50 && categories.map { it.id }.distinct().size == categories.size) { "Use 1–50 unique allocation categories." }
    require(categories.all { it.id.isNotBlank() && it.id.length <= 64 && it.name.isNotBlank() && it.name.length <= 100 && it.basisPoints in 0..10000 && it.categoryIds.size <= 500 && it.categoryIds.all { id -> id.isNotBlank() && id.length <= 100 } && (!it.savings || it.categoryIds.isEmpty()) }) { "Check allocation names, percentages and spending categories." }
    require(categories.count { it.remainder } <= 1) { "Choose one flexible allocation category." }
    require(categories.sumOf { it.basisPoints } == 10000) { "Total allocation must equal 100%." }
    val mapped = categories.flatMap { it.categoryIds }
    require(mapped.distinct().size == mapped.size) { "Each spending category can belong to only one allocation." }
}
fun validPlannerMonth(text: String) = runCatching { YearMonth.parse(text).let { it.year in 1970..2999 && it.toString() == text } }.getOrDefault(false)
fun validateFinanceMonth(value: FinanceMonth) {
    require(validPlannerMonth(value.month)) { "Invalid planner month." }
    val occurrences = value.occurrences()
    require(occurrences.size <= 100000 && occurrences.map { it.ruleId to it.date }.distinct().size == occurrences.size) { "Duplicate or excessive planned payments." }
    occurrences.forEach {
        require(it.ruleId.isNotBlank() && it.ruleId.length <= 64 && YearMonth.from(LocalDate.parse(it.date)).toString() == value.month && it.name.isNotBlank() && it.name.length <= 200 && it.cents in 1..my.fuelledger.domain.MAX_CENTS && it.kind in listOf("Income", "Expense") && it.categoryId.length <= 100 && it.payment in paymentMethods) { "Invalid planned payment." }
    }
}
/** Same anchored recurrence used by automatic posting. The posting cursor never alters the plan. */
fun plannedOccurrences(rules: List<FinanceRecurring>, month: YearMonth): List<PlannedOccurrence> = rules.filter { it.enabled && it.cents > 0 }.flatMap { rule ->
    validateFinanceRecurring(rule)
    val anchor = LocalDate.parse(rule.startDate)
    val first = month.atDay(1)
    val last = minOf(month.atEndOfMonth(), rule.endDate?.let(LocalDate::parse) ?: month.atEndOfMonth())
    var due = when (rule.frequency) {
        "Monthly" -> month.atDay(minOf(anchor.dayOfMonth, month.lengthOfMonth()))
        "Weekly" -> anchor.plusWeeks(maxOf(0, ChronoUnit.DAYS.between(anchor, first) / 7))
        else -> maxOf(anchor, first)
    }
    buildList {
        while (due <= last) {
            if (due >= anchor && due >= first) add(PlannedOccurrence(rule.id, due.toString(), rule.name, rule.cents, rule.categoryId, rule.kind, rule.payment))
            due = nextFinanceOccurrence(anchor, due, rule.frequency)
        }
    }
}
fun makeFinanceMonth(month: YearMonth, rules: List<FinanceRecurring>) = FinanceMonth(month.toString(), backupJson.encodeToString(plannedOccurrences(rules, month)))

fun variableExpensesForMonth(month: YearMonth, occurrences: List<PlannedOccurrence>, expenses: List<Expense>, zone: ZoneId): List<Expense> {
    val reserved = occurrences.filter { it.kind == "Expense" }.map { it.ruleId to it.date }.toSet()
    return expenses.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month &&
        (it.recurringRuleId == null || (it.recurringRuleId to it.recurringDate) !in reserved) &&
        !(it.kind == "Refund" && expenses.any { original -> original.id == it.refundOf && (original.recurringRuleId to original.recurringDate) in reserved }) }
}
data class MonthlyMoney(val expected: Long, val fixed: Long, val actualIncome: Long, val actualExpenses: Long,
    val variableExpenses: Long) {
    val plannedAvailable get() = expected - fixed
    val actualNet get() = actualIncome - actualExpenses
    val remainingAllowance get() = plannedAvailable - variableExpenses
}
fun monthlyMoney(month: YearMonth, occurrences: List<PlannedOccurrence>, income: List<FinanceIncome>,
    expenses: List<Expense>, zone: ZoneId = ZoneId.systemDefault()): MonthlyMoney {
    val actual = expenses.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month }
    return MonthlyMoney(occurrences.filter { it.kind == "Income" }.sumOf { it.cents },
        occurrences.filter { it.kind == "Expense" }.sumOf { it.cents }, financeMonthlyIncome(income, month, zone),
        actual.sumOf { it.cents }, variableExpensesForMonth(month, occurrences, expenses, zone).sumOf { it.cents })
}
data class AllocationAmount(val category: AllocationCategory, val allocated: Long, val spent: Long) {
    val remaining get() = allocated - spent
}
/** Largest-remainder rounding keeps every sen and never assigns negative money. */
fun allocationAmounts(available: Long, categories: List<AllocationCategory>, expenses: List<Expense>,
    month: YearMonth, zone: ZoneId = ZoneId.systemDefault(), reserved: List<PlannedOccurrence> = emptyList()): List<AllocationAmount> {
    validateAllocationPlan(allocationPlan("default", categories))
    val positive = maxOf(0, available)
    val parts = categories.map { java.math.BigInteger.valueOf(positive).multiply(java.math.BigInteger.valueOf(it.basisPoints.toLong())).divideAndRemainder(java.math.BigInteger.valueOf(10000)) }
    val amounts = parts.map { it[0].longValueExact() }.toMutableList()
    val leftover = (positive - amounts.sum()).toInt()
    parts.indices.sortedWith(compareByDescending<Int> { parts[it][1] }.thenBy { it }).take(leftover).forEach { amounts[it]++ }
    val variable = variableExpensesForMonth(month, reserved, expenses, zone)
    return categories.mapIndexed { index, category -> AllocationAmount(category, amounts[index],
        if (category.savings) 0 else variable.filter { it.categoryId in category.categoryIds }.sumOf { it.cents }) }
}
@Dao
interface PlannerDao {
    @Query("SELECT * FROM finance_months ORDER BY month") fun months(): Flow<List<FinanceMonth>>
    @Query("SELECT * FROM finance_allocations ORDER BY scope") fun allocations(): Flow<List<AllocationPlan>>
    @Query("SELECT * FROM finance_months ORDER BY month") suspend fun allMonths(): List<FinanceMonth>
    @Query("SELECT * FROM finance_allocations ORDER BY scope") suspend fun allAllocations(): List<AllocationPlan>
    @Upsert suspend fun putMonth(value: FinanceMonth)
    @Upsert suspend fun putAllocation(value: AllocationPlan)
    @Query("DELETE FROM finance_allocations WHERE scope = :scope") suspend fun deleteAllocation(scope: String)
    @Query("DELETE FROM finance_months") suspend fun clearMonths()
    @Query("DELETE FROM finance_allocations") suspend fun clearAllocations()
}
