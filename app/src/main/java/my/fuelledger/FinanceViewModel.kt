package my.fuelledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import my.fuelledger.data.*

data class FinanceState(val income: List<FinanceIncome> = emptyList(), val budgets: List<FinanceBudget> = emptyList(),
    val recurring: List<FinanceRecurring> = emptyList(), val months: List<FinanceMonth> = emptyList(),
    val allocations: List<AllocationPlan> = emptyList(), val expenses: List<Expense> = emptyList(), val categories: List<Category> = emptyList())

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val ledger = (application as LedgerApplication).repository
    private val finance = FinanceRepository(ledger.database)
    private val core = combine(finance.income, finance.budgets, finance.recurring, ledger.expenses, ledger.categories) { i, b, r, e, c ->
        FinanceState(income = i, budgets = b, recurring = r, expenses = e, categories = c)
    }
    val state = combine(core, ledger.database.plannerDao().months(), ledger.database.plannerDao().allocations()) { value, months, allocations ->
        value.copy(months = months, allocations = allocations)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceState())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun dismissError() { _error.value = null }
    private fun perform(done: () -> Unit, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action(); runCatching { my.fuelledger.widgets.LedgerWidgets.update(getApplication()) }; runCatching { my.fuelledger.finance.PlannerReminders.update(getApplication(), ledger) } }; done() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _error.value = failure.message ?: "Unable to save the local financial record." }
            finally { _busy.value = false }
        }
    }
    fun openMonth(month: java.time.YearMonth) {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { finance.reconcile(); finance.ensureMonth(month); runCatching { my.fuelledger.widgets.LedgerWidgets.update(getApplication()) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _error.value = failure.message ?: "Unable to reconcile the local monthly plan." }
        }
    }
    fun saveAllocation(value: AllocationPlan, done: () -> Unit) = perform(done) { finance.saveAllocation(value) }
    fun resetAllocation(scope: String, done: () -> Unit) = perform(done) { finance.resetAllocation(scope) }
    fun markPaid(value: PlannedOccurrence, date: java.time.LocalDate, cents: Long, existingId: String?, done: () -> Unit) =
        perform(done) { finance.markPaid(value, date, cents, existingId) }
    fun saveIncome(value: FinanceIncome, done: () -> Unit) = perform(done) { finance.saveIncome(value) }
    fun deleteIncome(id: String, done: () -> Unit) = perform(done) { finance.deleteIncome(id) }
    fun saveBudget(value: FinanceBudget, done: () -> Unit) = perform(done) { finance.saveBudget(value) }
    fun deleteBudget(id: String, done: () -> Unit) = perform(done) { finance.deleteBudget(id) }
    fun saveRecurring(value: FinanceRecurring, done: () -> Unit) = perform(done) { finance.saveRecurring(value) }
    fun deleteRecurring(id: String, done: () -> Unit) = perform(done) { finance.deleteRecurring(id) }
}
