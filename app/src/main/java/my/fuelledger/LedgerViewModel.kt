package my.fuelledger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.data.*

data class LedgerState(val categories: List<Category> = emptyList(), val expenses: List<Expense> = emptyList(),
    val refuels: List<Refuel> = emptyList(), val settings: AppSettings = AppSettings())

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LedgerApplication).repository
    val state = combine(repository.categories, repository.expenses, repository.refuels, repository.preferences.settings) { c, e, r, s ->
        LedgerState(c, e, r, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerState())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _startupError = MutableStateFlow<String?>(null)
    val startupError = _startupError.asStateFlow()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    fun dismissError() { _operationError.value = null }
    private val _restore = MutableStateFlow<Backup?>(null)
    val restorePreview = _restore.asStateFlow()
    init { initialize() }
    fun initialize() {
        if (_busy.value) return
        _startupError.value = null
        perform(null, {}) { repository.initialize(); _ready.value = true }
    }
    private fun perform(message: String?, done: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                done()
                message?.let { events.send(it) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                val detail = error.message ?: "Operation failed. Please try again."
                if (!_ready.value) _startupError.value = detail
                else _operationError.value = detail
            } finally { _busy.value = false }
        }
    }
    fun saveExpense(expense: Expense, done: () -> Unit) = perform("Expense saved", done) { repository.saveExpense(expense) }
    fun deleteExpense(id: String, done: () -> Unit) = perform("Expense deleted", done) { repository.deleteExpense(id) }
    fun saveRefuel(refuel: Refuel, done: () -> Unit) = perform("Refuel and linked expense saved", done) { repository.saveRefuel(refuel) }
    fun deleteRefuel(id: String, done: () -> Unit) = perform("Refuel and linked expense deleted", done) { repository.deleteRefuel(id) }
    fun saveCategory(category: Category, done: () -> Unit) = perform("Category saved", done) { repository.saveCategory(category) }
    fun deleteCategory(id: String, done: () -> Unit) = perform("Category deleted; transactions moved to Others", done) { repository.deleteCategory(id) }
    fun saveSettings(settings: AppSettings, done: () -> Unit) = perform("Preferences saved", done) { repository.saveSettings(settings) }
    fun export(uri: Uri, kind: String) = perform("File saved") {
        val data = when (kind) {
            "expenses" -> repository.exportExpenses()
            "refuels" -> repository.exportRefuels()
            else -> backupJson.encodeToString(repository.backup())
        }
        if (kind == "backup") require(data.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024) {
            "Backup exceeds the 16 MB restore limit. Export CSV for an additional copy of your records."
        }
        val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open this document for writing.")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(data) }
    }
    fun inspectBackup(uri: Uri) = perform(null) {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Cannot open this backup.")
        val bytes = stream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() + count <= 16 * 1024 * 1024) { "Backup exceeds the 16 MB limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= 16 * 1024 * 1024) { "Backup exceeds the 16 MB limit." }
        val backup = backupJson.decodeFromString<Backup>(bytes.toString(Charsets.UTF_8))
        backup.validate()
        _restore.value = backup
    }
    fun cancelRestore() { _restore.value = null }
    fun confirmRestore() {
        val backup = _restore.value ?: return
        perform("Backup restored", { _restore.value = null }) { repository.restore(backup) }
    }
}
