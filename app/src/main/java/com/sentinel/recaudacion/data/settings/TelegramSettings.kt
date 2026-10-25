package com.sentinel.recaudacion.data.settings

/** Configuración de Telegram. El token es sensible: nunca se escribe en logs. */
data class TelegramSettings(
    val token: String = "",
    val groupChatId: String = "",
    val privateChatId: String = "",
    val sendToGroup: Boolean = false,
    val sendToPrivate: Boolean = false,
) {
    val hasToken: Boolean get() = token.isNotBlank()

    /** Está configurado si hay token y al menos un destino habilitado con su ID. */
    val isConfigured: Boolean
        get() = hasToken &&
            ((sendToGroup && groupChatId.isNotBlank()) ||
                (sendToPrivate && privateChatId.isNotBlank()))

    /** Token enmascarado para mostrar en pantalla sin revelarlo. */
    fun maskedToken(): String {
        if (token.isBlank()) return "(sin configurar)"
        if (token.length <= 8) return "••••"
        return "${token.take(4)}••••••${token.takeLast(4)}"
    }

    /** Validación: los destinos habilitados deben tener un chat id con formato válido. */
    val hasValidEnabledIds: Boolean
        get() = (!sendToGroup || isValidChatId(groupChatId)) &&
            (!sendToPrivate || isValidChatId(privateChatId))

    companion object {
        /**
         * Un chat id de Telegram es un **número** (p. ej. `123456789` para un privado, o
         * negativo para grupos: `-5144820304`, `-1001234567890`) o un `@usuario` público.
         * Un **enlace de invitación** (`t.me/+...`, `joinchat`, `https://...`) **NO** es un
         * chat id y se rechaza: hay que obtener el id numérico con getUpdates/@userinfobot.
         */
        fun isValidChatId(value: String): Boolean {
            val v = value.trim()
            if (v.isEmpty()) return false
            val looksLikeLink = v.contains("t.me", ignoreCase = true) ||
                v.contains("joinchat", ignoreCase = true) ||
                v.contains("http", ignoreCase = true) ||
                v.startsWith("+")
            if (looksLikeLink) return false
            return Regex("^-?\\d{5,}$").matches(v) || Regex("^@[A-Za-z0-9_]{5,}$").matches(v)
        }
    }
}
