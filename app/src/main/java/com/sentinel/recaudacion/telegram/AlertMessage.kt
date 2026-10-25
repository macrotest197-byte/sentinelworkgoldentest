package com.sentinel.recaudacion.telegram

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Construye los mensajes de Telegram. Incluye únicamente la información necesaria para
 * investigar el incidente. NO incluye saldos, contraseñas, credenciales ni capturas.
 */
object AlertMessage {

    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    fun testMessage(now: Date = Date()): String =
        "✅ SENTINEL — Notificación de prueba\n" +
            "Si recibís este mensaje, la conexión con Telegram funciona.\n" +
            "Hora: ${fmt.format(now)}"

    /** Texto para persona sin resolver / ambigua. */
    const val UNRESOLVED = "Sin resolver"
    const val AMBIGUOUS = "Sin resolver (asociación ambigua)"

    /**
     * Mensaje de alerta de detección. «Palabra detectada» es el identificador concreto que
     * coincidió; «Le corresponde a» es el nombre de la persona vinculada (o sin resolver /
     * ambigua). Incluye solo lo necesario; no transmite el texto general del operador.
     */
    fun alert(
        device: String,
        matchedValue: String,
        personName: String,
        typeLabel: String,
        now: Date = Date(),
    ): String =
        "🚨 SENTINEL — DETECCIÓN DE LISTA NEGRA\n" +
            "📱 Dispositivo: $device\n" +
            "🔎 Palabra detectada: $matchedValue\n" +
            "👤 Le corresponde a: $personName\n" +
            "🧾 Tipo de dato: $typeLabel\n" +
            "🕒 Fecha y hora: ${fmt.format(now)}"
}
