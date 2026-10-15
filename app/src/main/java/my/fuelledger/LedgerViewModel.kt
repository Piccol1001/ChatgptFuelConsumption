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
import my.fuelledger.receipts.*
import java.io.File

data class LedgerState(val categories: List<Category> = emptyList(), val expenses: List<Expense> = emptyList(),
    val refuels: List<Refuel> = emptyList(), val settings: AppSettings = AppSettings())
data class ReceiptWork(val refuelId: String, val fileName: String, val suggestions: ReceiptSuggestions? = null)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LedgerApplication).repository
    private val receipts = ReceiptStore(application)
    private val archives = ReceiptBackupArchive(application, receipts)
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
    private val _restore = MutableStateFlow<PreparedRestore?>(null)
    val restorePreview = _restore.asStateFlow()
    private val _receiptWork = MutableStateFlow<ReceiptWork?>(null)
    val receiptWork = _receiptWork.asStateFlow()
    fun clearReceiptWork() { _receiptWork.value = null }
    fun receiptFile(name: String): File = receipts.file(name)
    fun showError(message: String) { _operationError.value = message }
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
    fun saveRefuel(refuel: Refuel, done: () -> Unit) = perform("Refuel and linked expense saved", done) {
        refuel.receiptFile?.let { require(receipts.file(it).isFile) { "Receipt photo is missing. Choose it again or remove the attachment." } }
        repository.saveRefuel(refuel)
    }
    fun deleteRefuel(id: String, done: () -> Unit) = perform("Refuel and linked expense deleted", done) { repository.deleteRefuel(id) }
    fun saveCategory(category: Category, done: () -> Unit) = perform("Category saved", done) { repository.saveCategory(category) }
    fun deleteCategory(id: String, done: () -> Unit) = perform("Category deleted; transactions moved to Others", done) { repository.deleteCategory(id) }
    fun saveSettings(settings: AppSettings, done: () -> Unit) = perform("Preferences saved", done) { repository.saveSettings(settings) }
    fun export(uri: Uri, kind: String) = perform("File saved") {
        val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open this document for writing.")
        stream.use { output ->
            if (kind == "backup") archives.write(output, repository.backup())
            else output.bufferedWriter(Charsets.UTF_8).use {
                it.write(if (kind == "expenses") repository.exportExpenses() else repository.exportRefuels())
            }
        }
    }
    fun inspectBackup(uri: Uri) = perform(null) {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Cannot open this backup.")
        val prepared = stream.use { archives.read(it) }
        _restore.value?.close()
        _restore.value = prepared
    }
    fun cancelRestore() {
        val prepared = _restore.value
        _restore.value = null
        viewModelScope.launch(Dispatchers.IO) { prepared?.close() }
    }
    fun confirmRestore() {
        val prepared = _restore.value ?: return
        perform("Backup and receipt photos restored", { _restore.value = null }) {
            repository.restore(archives.materialize(prepared))
            prepared.close()
        }
    }
    fun importReceipt(refuelId: String, uri: Uri) = perform("Receipt photo attached") {
        _receiptWork.value = ReceiptWork(refuelId, receipts.importPhoto(uri))
    }
    fun scanReceipt(refuelId: String, fileName: String) = perform(null) {
        _receiptWork.value = ReceiptWork(refuelId, fileName, ReceiptScanner().scan(receipts.file(fileName)))
    }
    fun saveReceiptToGallery(fileName: String) = perform("Photo saved to the Fuel & Expense Tracker Receipts album") {
        receipts.saveToGallery(fileName)
    }
    override fun onCleared() {
        _restore.value?.close()
        super.onCleared()
    }
}
