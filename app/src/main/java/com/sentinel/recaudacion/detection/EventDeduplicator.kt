package com.sentinel.recaudacion.detection

import java.util.concurrent.ConcurrentHashMap

/**
 * Deduplicación de eventos por clave dentro de una ventana temporal. Garantiza que un
 * mismo evento (misma [key]) no genere incidente/alarma/Telegram duplicados.
 *
 * Es en memoria del proceso: suficiente para evitar duplicados del mismo evento. La
 * deduplicación persistente de los envíos a Telegram la mantiene además la cola (outbox).
 */
class EventDeduplicator(
    private val windowMs: Long = 120_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val recent = ConcurrentHashMap<String, Long>()

    /** Devuelve true si la clave es NUEVA (hay que procesar); false si es duplicada dentro de la ventana. */
    fun admit(key: String): Boolean {
        val t = now()
        recent.entries.removeIf { t - it.value > windowMs }
        val prev = recent[key]
        if (prev != null && t - prev <= windowMs) return false
        recent[key] = t
        return true
    }
}
