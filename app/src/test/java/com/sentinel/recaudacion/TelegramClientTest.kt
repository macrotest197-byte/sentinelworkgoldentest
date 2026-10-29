package com.sentinel.recaudacion

import com.sentinel.recaudacion.telegram.AlertMessage
import com.sentinel.recaudacion.telegram.SendOutcome
import com.sentinel.recaudacion.telegram.TelegramClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramClientTest {

    @Test
    fun okTrue_esSuccess() {
        val r = TelegramClient.parseResponse(200, """{"ok":true,"result":{"message_id":5}}""")
        assertTrue(r is SendOutcome.Success)
        assertTrue(r.isSuccess)
    }

    @Test
    fun okFalse_esApiErrorConDescripcion() {
        val r = TelegramClient.parseResponse(400, """{"ok":false,"description":"chat not found"}""")
        assertTrue(r is SendOutcome.ApiError)
        assertEquals("chat not found", (r as SendOutcome.ApiError).description)
        assertTrue(!r.isRetryable)
    }

    @Test
    fun jsonInvalido_con200_esApiError() {
        val r = TelegramClient.parseResponse(200, "no es json")
        assertTrue(r is SendOutcome.ApiError)
    }

    @Test
    fun jsonInvalido_conHttpError_reportaCodigo() {
        val r = TelegramClient.parseResponse(401, "")
        assertTrue(r is SendOutcome.ApiError)
        assertEquals(401, (r as SendOutcome.ApiError).httpCode)
    }

    @Test
    fun networkError_esReintentable() {
        val r = SendOutcome.NetworkError("timeout")
        assertTrue(r.isRetryable)
    }

    @Test
    fun mensajeAlerta_tieneFormatoSolicitado() {
        val now = java.util.Date(0)
        val msg = AlertMessage.alert(
            device = "Celular Pato",
            matchedValue = "patodm.nx",
            personName = "Pato Encargado",
            typeLabel = "Alias",
            wallet = "Mercado Pago",
            now = now,
        )
        val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(now)
        val esperado = "🚨 SENTINEL — DETECCIÓN DE LISTA NEGRA\n" +
            "📱 Dispositivo: Celular Pato · Billetera: Mercado Pago\n" +
            "🔎 Palabra detectada: patodm.nx\n" +
            "👤 Le corresponde a: Pato Encargado\n" +
            "🧾 Tipo de dato: Alias\n" +
            "🕒 Fecha y hora: $fecha"
        assertEquals(esperado, msg)
        assertTrue(!msg.contains("saldo"))
        assertTrue(!msg.contains("contraseña"))
        assertTrue(!msg.contains("token"))
    }

    @Test
    fun mensajeAlerta_personaSinResolver_noInventaNombre() {
        val msg = AlertMessage.alert(
            device = "Celular Pato",
            matchedValue = "00000031000000000001",
            personName = AlertMessage.UNRESOLVED,
            typeLabel = "CBU",
            wallet = "Ualá",
        )
        assertTrue(msg.contains("Le corresponde a: Sin resolver"))
    }
}
