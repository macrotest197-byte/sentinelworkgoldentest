package com.sentinel.recaudacion.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Almacenamiento seguro de la configuración de Telegram.
 *
 * Usa EncryptedSharedPreferences respaldado por Android Keystore. El token y los IDs
 * quedan cifrados en disco. Esta clase nunca registra el token en logs.
 */
class SecureSettingsStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "sentinel_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): TelegramSettings = TelegramSettings(
        token = prefs.getString(KEY_TOKEN, "").orEmpty(),
        groupChatId = prefs.getString(KEY_GROUP, "").orEmpty(),
        privateChatId = prefs.getString(KEY_PRIVATE, "").orEmpty(),
        sendToGroup = prefs.getBoolean(KEY_EN_GROUP, false),
        sendToPrivate = prefs.getBoolean(KEY_EN_PRIVATE, false),
    )

    fun save(settings: TelegramSettings) {
        prefs.edit()
            .putString(KEY_TOKEN, settings.token.trim())
            .putString(KEY_GROUP, settings.groupChatId.trim())
            .putString(KEY_PRIVATE, settings.privateChatId.trim())
            .putBoolean(KEY_EN_GROUP, settings.sendToGroup)
            .putBoolean(KEY_EN_PRIVATE, settings.sendToPrivate)
            .apply()
    }

    private companion object {
        const val KEY_TOKEN = "telegram_token"
        const val KEY_GROUP = "telegram_group_id"
        const val KEY_PRIVATE = "telegram_private_id"
        const val KEY_EN_GROUP = "telegram_enable_group"
        const val KEY_EN_PRIVATE = "telegram_enable_private"
    }
}
