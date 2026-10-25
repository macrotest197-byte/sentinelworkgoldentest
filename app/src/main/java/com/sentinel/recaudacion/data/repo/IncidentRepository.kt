package com.sentinel.recaudacion.data.repo

import com.sentinel.recaudacion.data.db.IncidentDao
import com.sentinel.recaudacion.data.db.IncidentEntity
import kotlinx.coroutines.flow.Flow

class IncidentRepository(private val dao: IncidentDao) {

    fun observe(query: String, severity: String): Flow<List<IncidentEntity>> =
        dao.filter(query.trim(), severity)

    val count: Flow<Int> = dao.count()
    val latest: Flow<IncidentEntity?> = dao.latest()

    suspend fun all(): List<IncidentEntity> = dao.all()

    suspend fun log(incident: IncidentEntity): Long = dao.insert(incident)

    suspend fun logConfirmedMatch(
        matchType: String,
        matchedValue: String,
        personId: String?,
        personName: String?,
        confidence: Double,
        severity: String,
        device: String,
        deviceId: String,
        source: String,
        app: String?,
        excerpt: String?,
        alarmStatus: String,
        telegramStatus: String,
    ): Long = dao.insert(
        IncidentEntity(
            device = device,
            deviceId = deviceId,
            matchType = matchType,
            matchedValue = matchedValue,
            ruleDescription = null,
            severity = severity,
            source = source,
            app = app,
            alarmStatus = alarmStatus,
            telegramStatus = telegramStatus,
            personId = personId,
            personName = personName,
            confidence = confidence,
            sourceExcerpt = excerpt?.take(240),
        )
    )

    /** Registra un incidente de prueba manual (alarma lanzada desde la interfaz). */
    suspend fun logManualTest(device: String, deviceId: String = "") {
        dao.insert(
            IncidentEntity(
                device = device,
                deviceId = deviceId,
                matchType = "PRUEBA",
                matchedValue = "(prueba de alarma)",
                ruleDescription = "Prueba manual desde la interfaz",
                severity = "ADVERTENCIA",
                source = "PRUEBA MANUAL",
                app = null,
                alarmStatus = "ACTIVADA",
                telegramStatus = "N/A",
            )
        )
    }
}
