package com.sentinel.recaudacion.telegram

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlertMessage {

    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    fun testMessage(now: Date = Date()): String =
        "✅ SENTINEL — Notificación de prueba\n" +
            "Si recibís este mensaje, la conexión con Telegram funciona.\n" +
            "Hora: ${fmt.format(now)}"

    const val UNRESOLVED = "Sin resolver"
    const val AMBIGUOUS = "Sin resolver (asociación ambigua)"

    fun alert(
        device: String,
        matchedValue: String,
        personName: String,
        typeLabel: String,
        wallet: String,
        now: Date = Date(),
    ): String =
        "🚨 SENTINEL — DETECCIÓN DE LISTA NEGRA\n" +
            "📱 Dispositivo: $device · Billetera: $wallet\n" +
            "🔎 Palabra detectada: $matchedValue\n" +
            "👤 Le corresponde a: $personName\n" +
            "🧾 Tipo de dato: $typeLabel\n" +
            "🕒 Fecha y hora: ${fmt.format(now)}"
}
