package com.sentinel.recaudacion.telegram

import com.sentinel.recaudacion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Resultado real de un intento de envío a la API de Telegram. */
sealed interface SendOutcome {
    data object Success : SendOutcome
    /** La API respondió, pero con ok=false (p. ej. token inválido, chat no encontrado). */
    data class ApiError(val httpCode: Int, val description: String) : SendOutcome
    /** No se pudo contactar la API (sin red, timeout). Candidato a reintento. */
    data class NetworkError(val message: String) : SendOutcome

    val isSuccess: Boolean get() = this is Success
    /** Los errores de red se reintentan; los de API (datos mal) no tiene sentido reintentarlos. */
    val isRetryable: Boolean get() = this is NetworkError
}

class TelegramClient(
    private val client: OkHttpClient = defaultClient(),
) {
    /**
     * Envía un mensaje. NO registra el token ni la URL (que lo contiene) en ningún log.
     */
    suspend fun sendMessage(token: String, chatId: String, text: String): SendOutcome =
        withContext(Dispatchers.IO) {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val body = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .build()
            val request = Request.Builder().url(url).post(body).build()
            try {
                client.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val outcome = parseResponse(resp.code, raw)
                    // Solo DEBUG: registra el message_id (dato público; el token va en la URL, no en el body).
                    if (BuildConfig.DEBUG && outcome is SendOutcome.Success) {
                        val msgId = runCatching {
                            JSONObject(raw).optJSONObject("result")?.optLong("message_id")
                        }.getOrNull()
                        android.util.Log.i("SentinelTgDebug", "Envío OK (debug): message_id=$msgId")
                    }
                    outcome
                }
            } catch (e: IOException) {
                // Algunas IOException incluyen la URL (que contiene el token): se redacta.
                SendOutcome.NetworkError(redactSecrets(e.message ?: "Error de red"))
            }
        }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /**
         * Función pura (testeable) que interpreta la respuesta de la API.
         * Nunca incluye el token: sólo usa el cuerpo JSON y el código HTTP.
         */
        /**
         * Redacta un token de bot de Telegram en cualquier texto (p. ej. una URL dentro de
         * un mensaje de excepción). Formato del token: dígitos + ':' + alfanumérico/_/-.
         */
        fun redactSecrets(text: String): String =
            text.replace(Regex("bot\\d{3,}:[A-Za-z0-9_-]{20,}"), "bot<redactado>")

        fun parseResponse(httpCode: Int, body: String): SendOutcome {
            return try {
                val json = JSONObject(body)
                if (json.optBoolean("ok", false)) {
                    SendOutcome.Success
                } else {
                    val desc = json.optString("description", "La API rechazó el mensaje")
                    SendOutcome.ApiError(httpCode, desc)
                }
            } catch (_: Exception) {
                if (httpCode in 200..299) {
                    SendOutcome.ApiError(httpCode, "Respuesta no reconocida de la API")
                } else {
                    SendOutcome.ApiError(httpCode, "HTTP $httpCode")
                }
            }
        }
    }
}
