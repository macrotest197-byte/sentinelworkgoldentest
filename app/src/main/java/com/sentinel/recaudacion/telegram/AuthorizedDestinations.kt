package com.sentinel.recaudacion.telegram

import com.sentinel.recaudacion.data.settings.TelegramSettings

/** Destino autorizado de envío. */
data class AuthorizedTarget(val destination: String, val chatId: String)

/**
 * Única fuente de verdad para resolver a qué chats se envía. Deriva EXCLUSIVAMENTE de la
 * configuración autorizada ([TelegramSettings], aprovisionada de forma segura). No acepta
 * destinos arbitrarios de una pantalla ni de la base de datos: lo que no esté aquí, no se
 * envía.
 */
object AuthorizedDestinations {
    const val GROUP = "GRUPO"
    const val PRIVATE = "PRIVADO"

    fun resolve(settings: TelegramSettings): List<AuthorizedTarget> = buildList {
        if (settings.sendToGroup && settings.groupChatId.isNotBlank()) {
            add(AuthorizedTarget(GROUP, settings.groupChatId.trim()))
        }
        if (settings.sendToPrivate && settings.privateChatId.isNotBlank()) {
            add(AuthorizedTarget(PRIVATE, settings.privateChatId.trim()))
        }
    }
}
