package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.settings.TelegramSettings
import com.sentinel.recaudacion.telegram.AuthorizedDestinations
import com.sentinel.recaudacion.telegram.TelegramClient
import com.sentinel.recaudacion.telegram.TelegramProvisioning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSecurityTest {

    private val fakeToken = "123456789:AAFakeTokenValue_para_test_1234567890"

    @Test
    fun errorDeRed_noRevelaElToken() {
        val leak = "Failed to connect to https://api.telegram.org/bot$fakeToken/sendMessage"
        val redacted = TelegramClient.redactSecrets(leak)
        assertTrue("debe redactar el token", !redacted.contains(fakeToken))
        assertTrue(redacted.contains("bot<redactado>"))
    }

    @Test
    fun destinos_soloDesdeConfiguracionAutorizada() {
        val s = TelegramSettings(
            token = fakeToken,
            groupChatId = "-1001111111111",
            privateChatId = "222222222",
            sendToGroup = true,
            sendToPrivate = false, // privado deshabilitado: NO debe aparecer
        )
        val targets = AuthorizedDestinations.resolve(s)
        assertEquals(1, targets.size)
        assertEquals(AuthorizedDestinations.GROUP, targets[0].destination)
        assertEquals("-1001111111111", targets[0].chatId)
    }

    @Test
    fun destinos_vacios_siNoHayIdAunqueEsteHabilitado() {
        val s = TelegramSettings(
            token = fakeToken,
            groupChatId = "",
            sendToGroup = true,
        )
        assertTrue(AuthorizedDestinations.resolve(s).isEmpty())
    }

    @Test
    fun aprovisionamiento_jsonValido_seAcepta() {
        val json = """
            {"token":"$fakeToken","groupChatId":"-1001234567890","sendToGroup":true,"sendToPrivate":false}
        """.trimIndent()
        val s = TelegramProvisioning.parse(json)
        assertTrue(s != null && s.isConfigured)
        assertEquals("-1001234567890", s!!.groupChatId)
    }

    @Test
    fun aprovisionamiento_sinToken_seRechaza() {
        val json = """{"groupChatId":"-100123","sendToGroup":true}"""
        assertNull(TelegramProvisioning.parse(json))
    }

    @Test
    fun aprovisionamiento_sinDestinoHabilitado_seRechaza() {
        val json = """{"token":"$fakeToken","groupChatId":"-100123","sendToGroup":false,"sendToPrivate":false}"""
        assertNull(TelegramProvisioning.parse(json))
    }
}
