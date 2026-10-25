package com.sentinel.recaudacion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Incidente del historial. Se registra cuando una fuente de detección autorizada informa
 * una coincidencia (Fase 5) o ante una prueba manual. Se minimiza la información personal:
 * se guarda el valor coincidente pero la interfaz lo muestra parcialmente oculto.
 */
@Entity(
    tableName = "incidents",
    indices = [Index("createdAt"), Index("severity")]
)
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val device: String,
    val deviceId: String = "",
    val matchType: String,
    val matchedValue: String,
    val ruleDescription: String? = null,
    val severity: String,
    val source: String,          // p. ej. "PRUEBA MANUAL" o nombre de una fuente autorizada
    val app: String? = null,
    val alarmStatus: String,     // "SOLICITADA" | "ACTIVADA" | "NO ACTIVADA" | "FALLIDA"
    val telegramStatus: String,  // "PENDIENTE" | "DUPLICADA" | "FALLIDO" | "SIN CONFIGURAR" | "N/A"
    val personId: String? = null,
    val personName: String? = null,
    val confidence: Double? = null,
    val sourceExcerpt: String? = null,
)
