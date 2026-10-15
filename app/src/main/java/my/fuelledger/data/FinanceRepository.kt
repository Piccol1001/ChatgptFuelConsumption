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
        if (value.kind == "Expense") requireCategory(value.categoryId)
        preserveCurrentPlan()
        dao.putRecurring(value)
        refreshCurrentPlan()
        reconcile()
    }
    suspend fun deleteRecurring(id: String) = database.withTransaction {
        preserveCurrentPlan(); dao.deleteRecurring(id); refreshCurrentPlan()
    }
    private suspend fun preserveCurrentPlan() { ensureMonth(YearMonth.now()) }
    private suspend fun refreshCurrentPlan() { database.plannerDao().putMonth(makeFinanceMonth(YearMonth.now(), dao.allRecurring())) }
    suspend fun ensureMonth(month: YearMonth): FinanceMonth = database.withTransaction {
        require(validPlannerMonth(month.toString()))
        val saved = database.plannerDao().allMonths().firstOrNull { it.month == month.toString() }
        if (saved != null && month < YearMonth.now()) saved
        else makeFinanceMonth(month, dao.allRecurring()).also { if (month <= YearMonth.now()) database.plannerDao().putMonth(it) }
    }
    suspend fun saveAllocation(value: AllocationPlan) = database.withTransaction {
        validateAllocationPlan(value)
        require(value.categories().flatMap { it.categoryIds }.all { id -> database.dao().allCategories().any { it.id == id } }) { "A spending category no longer exists." }
        if (value.scope == "default") freezePastAllocations()
        database.plannerDao().putAllocation(value)
    }
    private suspend fun freezePastAllocations() {
        val planner = database.plannerDao()
        val allocations = planner.allAllocations()
        val defaults = allocations.firstOrNull { it.scope == "default" }?.categories() ?: defaultAllocations
        for (month in planner.allMonths().filter { YearMonth.parse(it.month) < YearMonth.now() }) {
            if (allocations.none { it.scope == month.month }) planner.putAllocation(allocationPlan(month.month, defaults))
        }
    }
    suspend fun resetAllocation(scope: String) = database.plannerDao().deleteAllocation(scope)
    /** Existing occurrence links win even if a daily expense was entered separately. */
    suspend fun markPaid(occurrence: PlannedOccurrence, paidDate: LocalDate, cents: Long,
        existingExpenseId: String? = null, zone: ZoneId = ZoneId.systemDefault()) = database.withTransaction {
        val planned = ensureMonth(YearMonth.from(LocalDate.parse(occurrence.date))).occurrences()
            .firstOrNull { it.ruleId == occurrence.ruleId && it.date == occurrence.date && it.kind == "Expense" }
        require(planned != null) { "This payment is no longer planned. Refresh the planner." }
        val expenses = database.dao().allExpenses()
        val linked = expenses.firstOrNull { it.recurringRuleId == occurrence.ruleId && it.recurringDate == occurrence.date }
        if (existingExpenseId == null && linked != null) return@withTransaction
        require(linked == null || linked.id == existingExpenseId) { "This payment already has a linked expense." }
        if (existingExpenseId != null) {
            val existing = requireNotNull(database.dao().expense(existingExpenseId)) { "Expense no longer exists." }
            require(existing.fuelId == null && (existing.recurringRuleId == null || existing.id == linked?.id)) { "Expense already belongs to another payment." }
            database.dao().putExpense(existing.copy(recurringRuleId = occurrence.ruleId, recurringDate = occurrence.date))
        } else {
            val category = if (database.dao().allCategories().any { it.id == planned.categoryId }) planned.categoryId else OTHER_CATEGORY
            val value = Expense("recurring:${occurrence.ruleId}:${occurrence.date}", cents, category,
                paidDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), planned.name, payment = planned.payment,
                recurringRuleId = planned.ruleId, recurringDate = planned.date)
            validateExpense(value); requireCategory(value.categoryId)
            require(database.dao().expense(value.id) == null) { "A transaction ID conflicts with this payment." }
            database.dao().putExpense(value)
        }
    }

    private suspend fun requireCategory(id: String, allowOverall: Boolean = false) {
        require((allowOverall && id.isEmpty()) || database.dao().allCategories().any { it.id == id }) { "Category no longer exists." }
    }

    /** Both the occurrence and cursor commit together. Repeated launches/work runs cannot duplicate payments. */
    suspend fun reconcile(today: LocalDate? = null, zone: ZoneId = ZoneId.systemDefault()): Int = database.withTransaction {
        val through = today ?: LocalDate.now(zone)
        var inserted = 0
        for (rule in dao.allRecurring().filter { it.enabled && it.kind == "Expense" && !it.manualPayment }) {
            validateFinanceRecurring(rule)
            requireCategory(rule.categoryId)
            val anchor = LocalDate.parse(rule.startDate)
            var due = LocalDate.parse(rule.nextDate)
            var occurrences = 0
            // Bound one background run while retaining its cursor for the following run.
            while (due <= through && (rule.endDate == null || due <= LocalDate.parse(rule.endDate)) && occurrences < 36_600) {
                val expense = Expense(id = "recurring:${rule.id}:$due", cents = rule.cents, categoryId = rule.categoryId,
                    timestamp = due.atStartOfDay(zone).toInstant().toEpochMilli(), description = rule.name,
                    notes = rule.notes, payment = rule.payment, recurringRuleId = rule.id, recurringDate = due.toString())
                validateExpense(expense)
                val alreadyLinked = database.dao().occurrence(rule.id, due.toString())
                if (alreadyLinked != null) { /* A manually linked transaction already pays this occurrence. */ }
                else if (dao.insertOccurrence(expense) != -1L) inserted++
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
        val lastSaved = database.plannerDao().allMonths().maxByOrNull { it.month }?.month?.let(YearMonth::parse)
        val target = YearMonth.from(through)
        if (lastSaved != null && lastSaved < target) {
            var missed = lastSaved.plusMonths(1)
            while (missed < target) { ensureMonth(missed); missed = missed.plusMonths(1) }
        }
        freezePastAllocations()
        ensureMonth(target)
        inserted
    }

    /** Call inside the category deletion transaction. Colliding budgets are combined without losing planned amounts. */
    suspend fun reassignCategory(oldId: String, replacement: String) = database.withTransaction {
        if (oldId == replacement) return@withTransaction
        requireCategory(replacement)
        dao.reassignRecurringCategory(oldId, replacement)
        // Removed categories become unmapped instead of creating overlapping allocations.
        for (plan in database.plannerDao().allAllocations()) {
            database.plannerDao().putAllocation(allocationPlan(plan.scope, plan.categories().map { it.copy(categoryIds = it.categoryIds - oldId) }))
        }
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
