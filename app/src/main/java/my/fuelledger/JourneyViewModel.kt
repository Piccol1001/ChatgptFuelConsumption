package my.fuelledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import my.fuelledger.data.*

class JourneyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JourneyRepository((application as LedgerApplication).repository.database)
    val journeys = repository.journeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun dismissError() { _error.value = null }
    private fun perform(done: () -> Unit, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() }; done() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = error.message ?: "Unable to update the journey." }
            finally { _busy.value = false }
        }
    }
    fun save(value: SavedJourney, done: () -> Unit) = perform(done) { repository.save(value) }
    fun delete(id: String, done: () -> Unit) = perform(done) { repository.delete(id) }
}
