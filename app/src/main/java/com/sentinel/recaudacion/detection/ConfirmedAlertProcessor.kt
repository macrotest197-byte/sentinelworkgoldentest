package com.sentinel.recaudacion.detection

import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.alarm.AlarmService
import com.sentinel.recaudacion.telegram.AlertMessage
import com.sentinel.recaudacion.telegram.EnqueueResult
import com.sentinel.recaudacion.telegram.TelegramSendWorker

data class ProcessedAlert(
    val incidentId: Long,
    val alarmStatus: String,
    val telegramStatus: String,
)

/**
 * Handles a confirmed match delivered by a future explicitly authorized source adapter.
 * The simulated text screen must never call this processor.
 */
object ConfirmedAlertProcessor {
    // Evita incidente/alarma/Telegram duplicados ante el mismo evento (misma eventKey).
    private val deduplicator = EventDeduplicator()

    suspend fun process(
        app: SentinelApp,
        resolved: ResolvedMatch,
        source: String,
        eventKey: String,
        sourceApp: String? = null,
        authorizedExcerpt: String? = null,
    ): ProcessedAlert? {
        if (resolved.confidence < TextNormalizer.MIN_CONFIDENCE || source.isBlank() || eventKey.isBlank()) return null
        // Dedup del evento completo: un mismo evento no duplica incidente/alarma/Telegram.
        if (!deduplicator.admit(eventKey)) return null

        val identity = app.deviceIdentity
        val deviceName = identity.friendlyName
        // Â«Le corresponde aÂ»: nombre de la persona, o sin resolver / ambigua. No se inventa.
        val personDisplay = resolved.personName
            ?: if (resolved.ambiguous) AlertMessage.AMBIGUOUS else AlertMessage.UNRESOLVED
        val message = AlertMessage.alert(
            device = deviceName,
            matchedValue = resolved.matchedValue,
            personName = personDisplay,
            typeLabel = resolved.matchType.label,
            wallet = AccessibilityScanPolicy.appLabel(sourceApp),
        )
        val alarmStatus = runCatching {
            AlarmService.start(app.applicationContext, message)
            "SOLICITADA"
        }.getOrDefault("FALLIDA")

        val enqueue = runCatching {
            app.telegramRepository.enqueueAlert(message, eventKey, identity.id)
        }.getOrNull()
        val telegramStatus = when (enqueue) {
            is EnqueueResult.Queued -> {
                runCatching { TelegramSendWorker.enqueue(app.applicationContext) }
                "PENDIENTE"
            }
            EnqueueResult.Duplicate -> "DUPLICADA"
            EnqueueResult.NotConfigured, null -> "SIN CONFIGURAR"
        }
        val incidentId = app.incidentRepository.logConfirmedMatch(
            matchType = resolved.matchType.name,
            matchedValue = resolved.matchedValue,
            personId = resolved.personId,
            personName = resolved.personName, // null si sin resolver / ambigua
            confidence = resolved.confidence,
            severity = resolved.severity.name,
            device = deviceName,
            deviceId = identity.id,
            source = source,
            app = sourceApp,
            excerpt = authorizedExcerpt,
            alarmStatus = alarmStatus,
            telegramStatus = telegramStatus,
        )
        return ProcessedAlert(incidentId, alarmStatus, telegramStatus)
    }
}
