package com.sentinel.recaudacion.ui.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.telegram.AuthorizedDestinations
import com.sentinel.recaudacion.telegram.SendOutcome
import com.sentinel.recaudacion.telegram.TelegramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado de SOLO LECTURA para el operador. No contiene campos editables: la configuración
 * se aprovisiona de forma segura fuera de la interfaz (ver TelegramProvisioning).
 */
data class TelegramUiState(
    val configured: Boolean = false,
    val maskedToken: String = "(sin configurar)",
    val destinations: List<String> = emptyList(),
    val sending: Boolean = false,
    val status: String? = null,
)

class TelegramViewModel(
    private val repo: TelegramRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(load())
    val ui: StateFlow<TelegramUiState> = _ui.asStateFlow()

    val pendingCount: StateFlow<Int> =
        repo.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun load(): TelegramUiState {
        val s = repo.settings()
        return TelegramUiState(
            configured = s.isConfigured,
            maskedToken = s.maskedToken(),
            destinations = AuthorizedDestinations.resolve(s).map { it.destination },
        )
    }

    /** Reenvía el estado desde la configuración autorizada (p. ej. tras aprovisionar). */
    fun refresh() { _ui.value = load().copy(status = _ui.value.status) }

    /** Envío de prueba al destino autorizado. No modifica la configuración. */
    fun sendTest() {
        val current = _ui.value
        if (!current.configured) {
            _ui.value = current.copy(status = "Telegram no está configurado. Debe aprovisionarse de forma segura.")
            return
        }
        _ui.value = current.copy(sending = true, status = "Enviando prueba…")
        viewModelScope.launch {
            val results = repo.sendTest()
            val msg = if (results.isEmpty()) {
                "No hay destino autorizado configurado."
            } else {
                results.joinToString("\n") { r ->
                    val detail = when (val o = r.outcome) {
                        SendOutcome.Success -> "enviado ✅"
                        is SendOutcome.ApiError -> "error de API: ${o.description} ❌"
                        is SendOutcome.NetworkError -> "sin conexión: ${o.message} ❌"
                    }
                    "${r.destination}: $detail"
                }
            }
            _ui.value = _ui.value.copy(sending = false, status = msg)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SentinelApp
                TelegramViewModel(app.telegramRepository)
            }
        }
    }
}
