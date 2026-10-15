package my.fuelledger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.fuelledger.data.*
import my.fuelledger.receipts.*
import java.io.File

data class LedgerState(val categories: List<Category> = emptyList(), val expenses: List<Expense> = emptyList(),
    val refuels: List<Refuel> = emptyList(), val settings: AppSettings = AppSettings())
data class ReceiptWork(val ownerId: String, val fileName: String, val suggestions: ReceiptSuggestions? = null)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LedgerApplication
    private val repository get() = app.repository
    private val receipts = ReceiptStore(application, app.vault)
    private val archives = ReceiptBackupArchive(application, receipts, app.vault)
    private val receiptWrites = Mutex()
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
    private val _restore = MutableStateFlow<PreparedRestore?>(null)
    val restorePreview = _restore.asStateFlow()
    private val _receiptWork = MutableStateFlow<ReceiptWork?>(null)
    val receiptWork = _receiptWork.asStateFlow()

    // Database migration and key generation must never block composition.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state = _ready.flatMapLatest { ready ->
        if (!ready) flowOf(LedgerState())
        else combine(repository.categories, repository.expenses, repository.refuels,
            repository.preferences.settings) { c, e, r, s -> LedgerState(c, e, r, s) }
            .catch { failure ->
                _startupError.value = "Your local records could not be opened. Existing files were preserved. ${failure.message.orEmpty()}"
                _ready.value = false
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerState())

    init { initialize() }
    fun dismissError() { _operationError.value = null }
    fun showError(message: String) { _operationError.value = message }
    fun clearReceiptWork() { _receiptWork.value = null }
    suspend fun receiptBytes(name: String): ByteArray = withContext(Dispatchers.IO) { receipts.readPhoto(name) }

    fun initialize() {
        if (_busy.value) return
        _startupError.value = null
        perform(null) {
            repository.initialize()
            receipts.migrateLegacyPhotos()
            app.financeRepository.reconcile()
            app.scheduleRecurring()
            receiptWrites.withLock { pruneUnusedReceipts() }
            _ready.value = true
        }
    }

    private fun perform(message: String?, done: () -> Unit = {}, cleanup: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) { cleanup(); return }
        _busy.value = true
        val job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                done()
                message?.let { events.send(it) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                val detail = error.message ?: "Operation failed. Please try again."
                if (!_ready.value) _startupError.value = detail else _operationError.value = detail
            } finally { _busy.value = false }
        }
        // Also runs if cancellation prevents the coroutine/IO block from ever starting.
        job.invokeOnCompletion { cleanup() }
    }

    private suspend fun receiptReferences(): Set<String> =
        (repository.dao.allRefuels().mapNotNull { it.receiptFile } +
            repository.dao.allExpenses().mapNotNull { it.receiptFile }).toSet()

    private suspend fun deleteIfUnused(name: String?) {
        if (name != null && name !in receiptReferences()) {
            val file = receipts.file(name)
            check(!file.exists() || file.delete()) { "The unused receipt could not be removed." }
        }
    }
    private suspend fun cleanupAfterCommit(name: String?) {
        try { deleteIfUnused(name) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _operationError.value = "Your records were saved. An unused receipt could not be removed; cleanup will retry when the app opens."
        }
    }

    private suspend fun pruneUnusedReceipts() {
        val referenced = receiptReferences()
        File(app.filesDir, "receipts").listFiles().orEmpty().forEach { file ->
            val name = file.name.removeSuffix(".enc")
            if (file.isFile && file.name.endsWith(".jpg.enc") && name !in referenced)
                runCatching { if (receipts.file(name).canonicalFile == file.canonicalFile) file.delete() }
        }
    }

    fun discardReceipt(name: String) {
        viewModelScope.launch(Dispatchers.IO) { receiptWrites.withLock { cleanupAfterCommit(name) } }
    }
    fun saveExpense(expense: Expense, done: () -> Unit) = perform("Expense saved", done) {
        receiptWrites.withLock {
        expense.receiptFile?.let { require(receipts.file(it).isFile) { "Receipt photo is missing. Choose it again or remove the attachment." } }
        val previous = repository.dao.expense(expense.id)?.receiptFile
        repository.saveExpense(expense)
        if (previous != expense.receiptFile) cleanupAfterCommit(previous)
        }
    }
    fun deleteExpense(id: String, done: () -> Unit) = perform("Expense deleted", done) {
        receiptWrites.withLock {
        val previous = repository.dao.expense(id)?.receiptFile
        repository.deleteExpense(id)
        cleanupAfterCommit(previous)
        }
    }
    fun saveRefuel(refuel: Refuel, done: () -> Unit) = perform("Refuel and linked expense saved", done) {
        receiptWrites.withLock {
        refuel.receiptFile?.let { require(receipts.file(it).isFile) { "Receipt photo is missing. Choose it again or remove the attachment." } }
        val previous = repository.dao.allRefuels().firstOrNull { it.id == refuel.id }?.receiptFile
        repository.saveRefuel(refuel)
        if (previous != refuel.receiptFile) cleanupAfterCommit(previous)
        }
    }
    fun deleteRefuel(id: String, done: () -> Unit) = perform("Refuel and linked expense deleted", done) {
        receiptWrites.withLock {
        val previous = repository.dao.allRefuels().firstOrNull { it.id == id }?.receiptFile
        repository.deleteRefuel(id)
        cleanupAfterCommit(previous)
        }
    }
    fun saveCategory(category: Category, done: () -> Unit) = perform("Category saved", done) { repository.saveCategory(category) }
    fun deleteCategory(id: String, done: () -> Unit) = perform("Category deleted; transactions moved to Others", done) { repository.deleteCategory(id) }
    fun saveSettings(settings: AppSettings, done: () -> Unit) = perform("Preferences saved", done) { repository.saveSettings(settings) }

    fun export(uri: Uri, kind: String, password: CharArray? = null) {
        if (_busy.value) { password?.fill('\u0000'); return }
        perform("File saved", cleanup = { password?.fill('\u0000') }) {
            try {
                require(kind in listOf("backup", "expenses", "refuels")) { "Choose a supported export." }
                if (kind == "backup") require(password != null && password.size in 12..1024) { "Use a backup password containing 12–1024 characters." }
                val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open this document for writing.")
                stream.use { output ->
                    if (kind == "backup") archives.writeEncrypted(output, repository.backup(), requireNotNull(password))
                    else output.bufferedWriter(Charsets.UTF_8).use {
                        it.write(if (kind == "expenses") repository.exportExpenses() else repository.exportRefuels())
                    }
                }
            } finally { password?.fill('\u0000') }
        }
    }

    fun previewRestore(uri: Uri, password: CharArray? = null, allowLegacy: Boolean = false) {
        if (_busy.value) { password?.fill('\u0000'); return }
        perform(null, cleanup = { password?.fill('\u0000') }) {
            try {
                require(allowLegacy || password != null) { "Enter the backup password, or select Import legacy backup for an old JSON/ZIP file." }
                val stream = app.contentResolver.openInputStream(uri) ?: error("Cannot open this backup.")
                val prepared = stream.use {
                    if (allowLegacy) archives.read(it) else archives.readEncrypted(it, requireNotNull(password))
                }
                _restore.value?.close()
                _restore.value = prepared
            } finally { password?.fill('\u0000') }
        }
    }
    fun cancelRestore() {
        if (_busy.value) return
        val prepared = _restore.value
        _restore.value = null
        viewModelScope.launch(Dispatchers.IO) { prepared?.close() }
    }
    fun confirmRestore() {
        val prepared = _restore.value ?: return
        perform("Backup and receipt photos restored", { _restore.value = null }) {
            receiptWrites.withLock {
                val materialized = try { archives.materialize(prepared) }
                catch (failure: Throwable) { _restore.value = null; throw failure }
                try {
                    try { repository.restore(materialized) }
                    catch (pending: RestoreSettingsPendingException) { _operationError.value = pending.message }
                    try {
                        app.financeRepository.reconcile()
                        app.scheduleRecurring()
                        pruneUnusedReceipts()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        _operationError.value = "Your records were restored. Local scheduling or receipt cleanup needs another attempt when the app reopens."
                    }
                } catch (failure: Throwable) {
                    // A cancelled settings update may follow a committed DB transaction.
                    withContext(NonCancellable) {
                        (materialized.refuels.mapNotNull { it.receiptFile } + materialized.expenses.mapNotNull { it.receiptFile })
                            .distinct().forEach { runCatching { deleteIfUnused(it) } }
                    }
                    throw failure
                } finally {
                    prepared.close()
                    _restore.value = null
                }
            }
        }
    }
    fun importReceipt(ownerId: String, uri: Uri) = perform("Receipt photo attached") {
        _receiptWork.value = ReceiptWork(ownerId, receipts.importPhoto(uri))
    }
    fun scanReceipt(ownerId: String, fileName: String) = perform(null) {
        val bytes = receipts.readPhoto(fileName)
        try { _receiptWork.value = ReceiptWork(ownerId, fileName, ReceiptScanner().scan(bytes)) }
        finally { bytes.fill(0) }
    }
    override fun onCleared() {
        _restore.value?.close()
    }
}
