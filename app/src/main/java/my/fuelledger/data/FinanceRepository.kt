package my.fuelledger.data

import androidx.room.withTransaction
import my.fuelledger.domain.MAX_CENTS
import java.time.*

class FinanceRepository(private val database: LedgerDatabase) {
    val dao = database.financeDao()
    val income = dao.income()
    val budgets = dao.budgets()
    val recurring = dao.recurring()

    suspend fun saveIncome(value: FinanceIncome) { validateFinanceIncome(value); dao.putIncome(value) }
    suspend fun deleteIncome(id: String) = dao.deleteIncome(id)
    suspend fun saveBudget(value: FinanceBudget) = database.withTransaction {
        validateFinanceBudget(value)
        requireCategory(value.categoryId, allowOverall = true)
        require(dao.allBudgets().none { it.id != value.id && it.month == value.month && it.categoryId == value.categoryId }) {
            "This month already has a budget for that category. Edit its existing budget."
        }
        dao.putBudget(value)
    }
    suspend fun deleteBudget(id: String) = dao.deleteBudget(id)
    suspend fun saveRecurring(value: FinanceRecurring) = database.withTransaction {
        validateFinanceRecurring(value)
        requireCategory(value.categoryId)
        dao.putRecurring(value)
        reconcile()
    }
    suspend fun deleteRecurring(id: String) = dao.deleteRecurring(id)

    private suspend fun requireCategory(id: String, allowOverall: Boolean = false) {
        require((allowOverall && id.isEmpty()) || database.dao().allCategories().any { it.id == id }) { "Category no longer exists." }
    }

    /** Both the occurrence and cursor commit together. Repeated launches/work runs cannot duplicate payments. */
    suspend fun reconcile(today: LocalDate? = null, zone: ZoneId = ZoneId.systemDefault()): Int = database.withTransaction {
        val through = today ?: LocalDate.now(zone)
        var inserted = 0
        for (rule in dao.allRecurring().filter { it.enabled }) {
            validateFinanceRecurring(rule)
            requireCategory(rule.categoryId)
            val anchor = LocalDate.parse(rule.startDate)
            var due = LocalDate.parse(rule.nextDate)
            var occurrences = 0
            // Bound one background run while retaining its cursor for the following run.
            while (due <= through && occurrences < 36_600) {
                val expense = Expense(id = "recurring:${rule.id}:$due", cents = rule.cents, categoryId = rule.categoryId,
                    timestamp = due.atStartOfDay(zone).toInstant().toEpochMilli(), description = rule.name,
                    notes = rule.notes, payment = rule.payment, recurringRuleId = rule.id, recurringDate = due.toString())
                validateExpense(expense)
                if (dao.insertOccurrence(expense) != -1L) inserted++
                else {
                    val existing = database.dao().expense(expense.id)
                    require(existing != null && existing.recurringRuleId == rule.id && existing.recurringDate == due.toString()) {
                        "A transaction conflicts with this recurring payment. No scheduled payments were changed."
                    }
                }
                due = nextFinanceOccurrence(anchor, due, rule.frequency)
                occurrences++
            }
            if (due.toString() != rule.nextDate) dao.putRecurring(rule.copy(nextDate = due.toString()))
        }
        inserted
    }

    /** Call inside the category deletion transaction. Colliding budgets are combined without losing planned amounts. */
    suspend fun reassignCategory(oldId: String, replacement: String) = database.withTransaction {
        if (oldId == replacement) return@withTransaction
        requireCategory(replacement)
        dao.reassignRecurringCategory(oldId, replacement)
        for (budget in dao.allBudgets().filter { it.categoryId == oldId }) {
            val target = dao.allBudgets().firstOrNull { it.month == budget.month && it.categoryId == replacement }
            if (target == null) dao.putBudget(budget.copy(categoryId = replacement))
            else {
                val combined = Math.addExact(target.cents, budget.cents)
                require(combined <= MAX_CENTS) { "Combined category budgets exceed the supported amount. Edit them before deleting this category." }
                dao.deleteBudget(budget.id)
                dao.putBudget(target.copy(cents = combined))
            }
        }
    }
}
