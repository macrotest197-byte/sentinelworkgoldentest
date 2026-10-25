package com.sentinel.recaudacion.ui.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sentinel.recaudacion.SentinelApp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.sentinel.recaudacion.data.model.BlacklistRule
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.data.repo.BlacklistRepository
import com.sentinel.recaudacion.data.repo.SaveResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado del resumen de la lista negra para la pantalla principal. Distingue explícitamente
 * la carga en curso, el resultado real y el error: así la interfaz no presenta un "0" falso
 * mientras la consulta no terminó, ni oculta un fallo como si fuese una lista vacía.
 */
sealed interface BlacklistSummary {
    data object Loading : BlacklistSummary
    data class Ready(
        val total: Int,
        val activas: Int,
        val personas: Int,
        val identificadores: Int,
    ) : BlacklistSummary
    data class Error(val message: String) : BlacklistSummary

    companion object {
        /**
         * Deriva los conteos desde la lista persistida (única fuente de verdad). Las personas se
         * agrupan igual que el motor de detección ([BlacklistRepository.activePersons]); los
         * identificadores son las reglas activas que no son NOMBRE.
         */
        fun from(rules: List<BlacklistRule>): Ready {
            val activas = rules.filter { it.active }
            return Ready(
                total = rules.size,
                activas = activas.size,
                personas = activas
                    .groupBy { it.personId?.takeIf(String::isNotBlank) ?: "rule-${it.id}" }
                    .size,
                identificadores = activas.count { it.type != RuleType.NOMBRE },
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BlacklistViewModel(
    private val repo: BlacklistRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Disparador de relectura explícita. Room sólo re-emite ante escrituras hechas a través de
     * su propia conexión (InvalidationTracker); un aprovisionamiento aplicado por escritura
     * directa en SQLite no la notifica. Al incrementarse, [rules] re-suscribe una consulta Room
     * nueva, que emite el estado persistido actual. No es una segunda fuente de verdad: la verdad
     * sigue siendo la base; esto sólo fuerza volver a leerla cuando la pantalla se reanuda.
     */
    private val _refresh = MutableStateFlow(0)

    val rules: StateFlow<List<BlacklistRule>> =
        combine(_query, _refresh) { q, _ -> q }
            .flatMapLatest { repo.observe(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Resumen total de la lista negra (independiente del filtro de búsqueda), con estado de
     * carga/error explícito. Se recalcula al reanudar la pantalla ([refresh]) para reflejar
     * aprovisionamientos aplicados fuera de la app. Los conteos se derivan de la misma lista de
     * Room: no hay una segunda fuente de verdad.
     */
    val summary: StateFlow<BlacklistSummary> =
        _refresh
            .flatMapLatest {
                repo.observe("")
                    .map<List<BlacklistRule>, BlacklistSummary> { list -> BlacklistSummary.from(list) }
                    .catch { emit(BlacklistSummary.Error(it.message ?: "No se pudo leer la lista negra")) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BlacklistSummary.Loading)

    /** Fuerza una relectura desde Room (p. ej. al volver a la pantalla tras un aprovisionamiento). */
    fun refresh() {
        _refresh.value += 1
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearMessage() {
        _message.value = null
    }

    suspend fun load(id: Long): BlacklistRule? = repo.getById(id)

    fun save(rule: BlacklistRule, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.save(rule)) {
                is SaveResult.Ok -> {
                    _message.value = if (rule.id == 0L) "Regla agregada" else "Regla actualizada"
                    onDone(true)
                }
                SaveResult.Duplicate -> {
                    _message.value = "Ya existe una regla con ese tipo y valor"
                    onDone(false)
                }
                is SaveResult.Invalid -> {
                    _message.value = r.reason
                    onDone(false)
                }
            }
        }
    }

    fun toggleActive(rule: BlacklistRule, active: Boolean) {
        viewModelScope.launch { repo.setActive(rule, active) }
    }

    fun delete(rule: BlacklistRule) {
        viewModelScope.launch {
            repo.delete(rule)
            _message.value = "Regla eliminada"
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SentinelApp
                BlacklistViewModel(app.blacklistRepository)
            }
        }
    }
}
