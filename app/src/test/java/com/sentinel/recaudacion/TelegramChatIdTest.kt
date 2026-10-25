package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.settings.TelegramSettings
import com.sentinel.recaudacion.telegram.TelegramProvisioning
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramChatIdTest {

    private val fakeToken = "123456789:AAFakeTokenValue_para_test_1234567890"

    @Test
    fun idsNumericos_yUsuario_sonValidos() {
        assertTrue(TelegramSettings.isValidChatId("-5144820304"))     // grupo básico
        assertTrue(TelegramSettings.isValidChatId("-1001234567890"))  // supergrupo
        assertTrue(TelegramSettings.isValidChatId("123456789"))       // privado
        assertTrue(TelegramSettings.isValidChatId("@grupo_oficial"))  // público
    }

    @Test
    fun enlaceDeInvitacion_noEsChatId() {
        assertFalse(TelegramSettings.isValidChatId("t.me/+IcKzTwOpxn5iYjgx"))
        assertFalse(TelegramSettings.isValidChatId("https://t.me/joinchat/abc"))
        assertFalse(TelegramSettings.isValidChatId("+IcKzTwOpxn5iYjgx"))
        assertFalse(TelegramSettings.isValidChatId(""))
        assertFalse(TelegramSettings.isValidChatId("hola"))
        assertFalse(TelegramSettings.isValidChatId("123")) // muy corto
    }

    @Test
    fun aprovisionamiento_rechazaEnlaceComoId() {
        val json = """{"token":"$fakeToken","groupChatId":"t.me/+IcKzTwOpxn5iYjgx","sendToGroup":true}"""
        assertNull(TelegramProvisioning.parse(json))
    }

    @Test
    fun aprovisionamiento_aceptaIdNumerico() {
        val json = """{"token":"$fakeToken","groupChatId":"-5144820304","sendToGroup":true,"sendToPrivate":false}"""
        val s = TelegramProvisioning.parse(json)
        assertTrue(s != null && s.isConfigured && s.hasValidEnabledIds)
    }
}
