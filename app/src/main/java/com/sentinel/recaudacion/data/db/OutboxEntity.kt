package com.sentinel.recaudacion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cola local de mensajes de Telegram (outbox).
 *
 * Un registro por (mensaje × destino). Permite reintentos controlados, sobrevive a
 * cierres de la app y guarda el **resultado real** de cada envío (status + lastError).
 * [dedupKey] evita notificaciones duplicadas por el mismo incidente.
 */
@Entity(
    tableName = "telegram_outbox",
    indices = [Index("dedupKey"), Index("status")]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destination: String,       // "GRUPO" | "PRIVADO"
    val chatId: String,
    val text: String,
    val deviceId: String = "",
    val dedupKey: String,
    val status: String,            // "PENDIENTE" | "ENVIADO" | "FALLIDO"
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
