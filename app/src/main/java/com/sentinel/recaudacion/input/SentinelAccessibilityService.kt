package com.sentinel.recaudacion.input

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.detection.AccessibilityScanPolicy
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.ConfirmedAlertProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio de accesibilidad **opt-in** y acotado a billeteras autorizadas. Objetivo de esta fase:
 * **detectar y avisar**, NUNCA bloquear. No pulsa controles, no modifica campos, no toca transferencias.
 *
 * - Fail-closed: solo apps de [AccessibilityScanPolicy.ALLOWED_PACKAGES]; excluye campos de contraseña.
 * - Reutiliza el motor existente: [BlacklistPersonMatcher.resolve] + [ConfirmedAlertProcessor]
 *   (mismo incidente + alarma + cola de Telegram; sin segundo matcher ni segundo pipeline).
 * - No almacena pantallas ni envía texto general: la alerta lleva solo los campos mínimos.
 * - Dedup por destinatario mientras el usuario permanece en la pantalla (+ dedup del procesador).
 */
class SentinelAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var persons: List<BlacklistPerson> = emptyList()
    @Volatile private var lastKey: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { refreshPersons() }
    }

    private suspend fun refreshPersons() {
        runCatching { persons = (application as SentinelApp).blacklistRepository.activePersons() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString()
        if (pkg == null || pkg !in AccessibilityScanPolicy.ALLOWED_PACKAGES) return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Nueva pantalla: se puede volver a avisar por el mismo destinatario; refrescar reglas.
            lastKey = null
            scope.launch { refreshPersons() }
            return
        }
        val relevant = e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            e.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return

        val node = e.source ?: return
        val text: String
        val isPwd: Boolean
        try {
            isPwd = node.isPassword
            text = node.text?.toString().orEmpty()
        } finally {
            runCatching { node.recycle() }
        }
        if (!AccessibilityScanPolicy.shouldInspect(pkg, isPwd, text)) return
        if (com.sentinel.recaudacion.BuildConfig.DEBUG) {
            // Solo DEBUG: evidencia de evento recibido SIN contenido (solo paquete, tipo y largo).
            android.util.Log.d("SentinelA11y", "evt pkg=$pkg type=${e.eventType} len=${text.trim().length}")
        }
        inspect(pkg, text.trim())
    }

    /** Evalúa el texto del campo cambiado contra la lista negra y, si hay coincidencia, avisa. */
    private fun inspect(pkg: String, text: String) {
        scope.launch {
            if (persons.isEmpty()) refreshPersons()
            val resolved = BlacklistPersonMatcher.resolve(text, persons)
            if (com.sentinel.recaudacion.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "SentinelA11y",
                    "match pkg=$pkg -> " + (resolved?.let {
                        "tipo=${it.matchType.name} persona=${it.personId ?: "amb"} ambiguo=${it.ambiguous}"
                    } ?: "sin coincidencia"),
                )
            }
            if (resolved == null) return@launch
            val key = "${resolved.personId ?: "amb"}|${resolved.matchType.name}|${resolved.matchedValue}"
            if (key == lastKey) return@launch // mismo destinatario en la misma pantalla → no repetir
            lastKey = key
            // Modo diagnóstico SOLO DEBUG: registra la coincidencia (mínimo, sin texto) y NO dispara
            // alarma ni Telegram. Se activa creando el archivo-bandera por adb (run-as).
            if (com.sentinel.recaudacion.BuildConfig.DEBUG && diagnosticModeEnabled()) {
                android.util.Log.i(
                    "SentinelA11y",
                    "DIAG detecta pkg=$pkg tipo=${resolved.matchType.name} " +
                        "persona=${resolved.personId ?: "amb"} ambiguo=${resolved.ambiguous} (SIN alerta)",
                )
                return@launch
            }
            runCatching {
                ConfirmedAlertProcessor.process(
                    app = application as SentinelApp,
                    resolved = resolved,
                    source = "Accesibilidad · ${AccessibilityScanPolicy.appLabel(pkg)}",
                    eventKey = key,
                    sourceApp = pkg,
                    authorizedExcerpt = null, // no se almacena el texto de la pantalla
                )
            }
        }
    }

    /** SOLO DEBUG: ¿está el archivo-bandera de diagnóstico? (evita alarmas/Telegram en las pruebas). */
    private fun diagnosticModeEnabled(): Boolean =
        runCatching { java.io.File(filesDir, "a11y_diag").exists() }.getOrDefault(false)

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        persons = emptyList()
        super.onDestroy()
    }
}
