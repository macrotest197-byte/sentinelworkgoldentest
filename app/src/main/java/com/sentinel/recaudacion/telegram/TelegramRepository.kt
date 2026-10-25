package com.sentinel.recaudacion.telegram

import com.sentinel.recaudacion.data.db.OutboxDao
import com.sentinel.recaudacion.data.db.OutboxEntity
import com.sentinel.recaudacion.data.settings.SecureSettingsStore
import com.sentinel.recaudacion.data.settings.TelegramSettings
import kotlinx.coroutines.flow.Flow

/** Estado de deduplicación al intentar encolar una alerta. */
sealed interface EnqueueResult {
    data class Queued(val count: Int) : EnqueueResult
    data object Duplicate : EnqueueResult
    data object NotConfigured : EnqueueResult
}

/** Resultado de un envío de prueba por destino. */
data class TestSendResult(val destination: String, val outcome: SendOutcome)

class TelegramRepository(
    private val store: SecureSettingsStore,
    private val outbox: OutboxDao,
    private val client: TelegramClient = TelegramClient(),
) {
    fun settings(): TelegramSettings = store.load()
    fun saveSettings(settings: TelegramSettings) = store.save(settings)

    val pendingCount: Flow<Int> = outbox.countPending()
    fun recentSends(limit: Int = 50): Flow<List<OutboxEntity>> = outbox.recent(limit)

    // Resuelve destinos ÚNICAMENTE desde la configuración autorizada (fuente central única).
    private fun enabledTargets(s: TelegramSettings): List<Pair<String, String>> =
        AuthorizedDestinations.resolve(s).map { it.destination to it.chatId }

    /** Envío de prueba inmediato. Devuelve el resultado real por destino. */
    suspend fun sendTest(): List<TestSendResult> {
        val s = settings()
        val targets = enabledTargets(s)
        if (!s.hasToken || targets.isEmpty()) return emptyList()
        val text = AlertMessage.testMessage()
        return targets.map { (dest, chatId) ->
            val outcome = client.sendMessage(s.token, chatId, text)
            record(dest, chatId, text, dedupKey = "test-${System.currentTimeMillis()}", outcome)
            TestSendResult(dest, outcome)
        }
    }

    /**
     * Encola una alerta para enviar, con deduplicación por [dedupKey].
     * La alarma local NO depende de esto: si Telegram falla, la cola reintenta aparte.
     */
    suspend fun enqueueAlert(text: String, dedupKey: String, deviceId: String = ""): EnqueueResult {
        val s = settings()
        val targets = enabledTargets(s)
        if (!s.hasToken || targets.isEmpty()) return EnqueueResult.NotConfigured

        val since = System.currentTimeMillis() - DEDUP_WINDOW_MS
        if (outbox.countRecentByDedup(dedupKey, since) > 0) return EnqueueResult.Duplicate

        targets.forEach { (dest, chatId) ->
            outbox.insert(
                OutboxEntity(
                    destination = dest,
                    chatId = chatId,
                    text = text,
                    deviceId = deviceId,
                    dedupKey = dedupKey,
                    status = STATUS_PENDING,
                )
            )
        }
        return EnqueueResult.Queued(targets.size)
    }

    /** Procesa la cola de pendientes con reintentos controlados. Devuelve cuántos quedaron pendientes. */
    suspend fun processPending(): Int {
        val s = settings()
        if (!s.hasToken) return outbox.pending().size
        for (row in outbox.pending()) {
            val outcome = client.sendMessage(s.token, row.chatId, row.text)
            val attempts = row.attempts + 1
            val newStatus = when {
                outcome.isSuccess -> STATUS_SENT
                outcome.isRetryable && attempts < MAX_ATTEMPTS -> STATUS_PENDING
                else -> STATUS_FAILED
            }
            outbox.update(
                row.copy(
                    status = newStatus,
                    attempts = attempts,
                    lastError = (outcome as? SendOutcome.ApiError)?.description
                        ?: (outcome as? SendOutcome.NetworkError)?.message,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        return outbox.pending().size
    }

    private suspend fun record(
        dest: String,
        chatId: String,
        text: String,
        dedupKey: String,
        outcome: SendOutcome,
    ) {
        outbox.insert(
            OutboxEntity(
                destination = dest,
                chatId = chatId,
                text = text,
                dedupKey = dedupKey,
                status = if (outcome.isSuccess) STATUS_SENT else STATUS_FAILED,
                attempts = 1,
                lastError = (outcome as? SendOutcome.ApiError)?.description
                    ?: (outcome as? SendOutcome.NetworkError)?.message,
            )
        )
    }

    companion object {
        const val DEST_GROUP = "GRUPO"
        const val DEST_PRIVATE = "PRIVADO"
        const val STATUS_PENDING = "PENDIENTE"
        const val STATUS_SENT = "ENVIADO"
        const val STATUS_FAILED = "FALLIDO"
        const val MAX_ATTEMPTS = 5
        const val DEDUP_WINDOW_MS = 120_000L // 2 minutos
    }
}
