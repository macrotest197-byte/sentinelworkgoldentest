package com.sentinel.recaudacion.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.csv.IncidentCsv
import com.sentinel.recaudacion.data.db.IncidentEntity
import com.sentinel.recaudacion.data.repo.IncidentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repo: IncidentRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _severity = MutableStateFlow("") // "", "ADVERTENCIA", "CRITICA"
    val severity: StateFlow<String> = _severity.asStateFlow()

    val incidents: StateFlow<List<IncidentEntity>> =
        combine(_query, _severity) { q, s -> q to s }
            .flatMapLatest { (q, s) -> repo.observe(q, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQuery(v: String) { _query.value = v }
    fun onSeverity(v: String) { _severity.value = v }

    suspend fun exportCsv(): String = IncidentCsv.export(repo.all())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SentinelApp
                HistoryViewModel(app.incidentRepository)
            }
        }
    }
}
