package com.sentinel.recaudacion.telegram

import android.content.Context
import com.sentinel.recaudacion.data.settings.SecureSettingsStore
import com.sentinel.recaudacion.data.settings.TelegramSettings
import org.json.JSONObject
import java.io.File

/**
 * Aprovisionamiento de la configuración autorizada de Telegram, FUERA de la interfaz del
 * operador. El administrador coloca un archivo JSON en el directorio privado de la app;
 * al iniciar, SENTINEL lo valida, lo guarda cifrado (Keystore) y **borra el archivo**.
 *
 * Así el token y los destinos:
 *  - no viven en el código, recursos ni APK,
 *  - no son editables por el operador,
 *  - quedan como única configuración autorizada.
 *
 * Ubicación esperada: getExternalFilesDir(null)/sentinel_telegram_provision.json
 * (ruta privada de la app; el administrador la coloca por USB/adb o gestor de archivos).
 */
object TelegramProvisioning {

    const val FILE_NAME = "sentinel_telegram_provision.json"

    sealed interface Result {
        data object NotFound : Result
        data class Imported(val destinations: List<String>) : Result
        data class Invalid(val reason: String) : Result
    }

    fun provisioningFile(context: Context): File =
        File(context.getExternalFilesDir(null), FILE_NAME)

    fun importIfPresent(context: Context, store: SecureSettingsStore): Result {
        val file = provisioningFile(context)
        if (!file.exists()) return Result.NotFound
        val settings = try {
            parse(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        } ?: run {
            // No se registra el contenido (puede incluir el token).
            return Result.Invalid("Archivo de aprovisionamiento inválido o incompleto")
        }
        store.save(settings)
        // Borrar el archivo con el secreto una vez importado.
        runCatching { file.delete() }
        val dests = AuthorizedDestinations.resolve(settings).map { it.destination }
        return Result.Imported(dests)
    }

    /**
     * Parseo puro (testeable). Devuelve null si falta el token o no hay al menos un destino
     * habilitado con su ID. NO lanza con el contenido del token en el mensaje.
     */
    fun parse(json: String): TelegramSettings? {
        val o = JSONObject(json)
        val settings = TelegramSettings(
            token = o.optString("token", "").trim(),
            groupChatId = o.optString("groupChatId", "").trim(),
            privateChatId = o.optString("privateChatId", "").trim(),
            sendToGroup = o.optBoolean("sendToGroup", false),
            sendToPrivate = o.optBoolean("sendToPrivate", false),
        )
        // Debe estar configurado Y los ids de los destinos habilitados deben ser válidos
        // (un enlace de invitación t.me/+... NO es un chat id).
        return if (settings.isConfigured && settings.hasValidEnabledIds) settings else null
    }
}
