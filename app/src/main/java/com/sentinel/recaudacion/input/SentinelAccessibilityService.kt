package com.sentinel.recaudacion.input

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.sentinel.recaudacion.BuildConfig
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.detection.AccessibilityScanPolicy
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.ConfirmedAlertProcessor
import com.sentinel.recaudacion.detection.ResolvedMatch
import com.sentinel.recaudacion.detection.ScreenScanPolicy
import com.sentinel.recaudacion.detection.TextNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio de accesibilidad **opt-in** y acotado a billeteras autorizadas. **Detecta y avisa**,
 * NUNCA bloquea. No pulsa controles, no modifica campos, no toca transferencias.
 *
 * Dos fuentes, un solo circuito:
 *  1. **Campo** ([MatchScope.CAMPO]): el texto del campo que cambió. Es la vía exacta.
 *  2. **Pantalla** ([MatchScope.PANTALLA]): el árbol de la ventana, con retardo, para atrapar el
 *     titular que la billetera resuelve después de escribir el alias. Es la red de seguridad.
 *
 * Ambas reutilizan [BlacklistPersonMatcher.resolve] y [ConfirmedAlertProcessor]; no hay segundo
 * pipeline. La alerta del escaneo usa la misma eventKey que el campo, así que la dedup del
 * procesador evita avisar dos veces por la misma persona.
 */
class SentinelAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var persons: List<BlacklistPerson> = emptyList()
    @Volatile private var lastKey: String? = null
    @Volatile private var lastScreenKey: String? = null
    private var screenJob: Job? = null

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
            // Nueva pantalla: se rearma todo y se refrescan las reglas.
            lastKey = null
            lastScreenKey = null
            screenJob?.cancel()
            scope.launch { refreshPersons() }
            return
        }
        val relevant = e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            e.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return

        // Vía 1: el campo que cambió.
        inspectField(pkg, e)

        // Vía 2: la pantalla, con retardo, para cuando la billetera resuelve el titular.
        scheduleScreenScan(pkg)
    }

    private fun inspectField(pkg: String, e: AccessibilityEvent) {
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
        if (BuildConfig.DEBUG) {
            android.util.Log.d("SentinelA11y", "evt pkg=$pkg type=${e.eventType} len=${text.trim().length}")
        }
        inspect(pkg, text.trim())
    }

    /** Evalúa el texto del campo cambiado contra la lista negra y, si hay coincidencia, avisa. */
    private fun inspect(pkg: String, text: String) {
        scope.launch {
            if (persons.isEmpty()) refreshPersons()
            val resolved = BlacklistPersonMatcher.resolve(text, persons)
            logMatch(pkg, "campo", resolved)
            if (resolved == null) return@launch
            val key = eventKey(resolved)
            if (key == lastKey) return@launch
            lastKey = key
            if (BuildConfig.DEBUG && diagnosticModeEnabled()) {
                android.util.Log.i("SentinelA11y", "DIAG campo pkg=$pkg $key (SIN alerta)")
                return@launch
            }
            deliver(pkg, resolved, "Accesibilidad · ${AccessibilityScanPolicy.appLabel(pkg)}", key)
        }
    }

    private fun scheduleScreenScan(pkg: String) {
        screenJob?.cancel()
        screenJob = scope.launch {
            delay(SCREEN_SCAN_DELAY_MS)
            scanScreen(pkg)
        }
    }

    /**
     * Lee la ventana completa y la evalúa con [TextNormalizer.MatchScope.PANTALLA]. Solo actúa
     * en modo diagnóstico; en producción se habilitará después de la medición en dispositivo.
     */
    private suspend fun scanScreen(pkg: String) {
        val nodes = runCatching { ScreenTextCollector.collect(rootInActiveWindow) }.getOrDefault(emptyList())
        if (nodes.isEmpty()) return
        val text = nodes.joinToString(" ")
        if (!ScreenScanPolicy.shouldScan(pkg, text)) return

        val screenKey = ScreenScanPolicy.screenKey(text)
        if (screenKey == lastScreenKey) return
        lastScreenKey = screenKey

        if (persons.isEmpty()) refreshPersons()
        val resolved = BlacklistPersonMatcher.resolve(text, persons, TextNormalizer.MatchScope.PANTALLA)
        dumpDiagnostic(pkg, nodes, resolved)
        logMatch(pkg, "pantalla", resolved)
        if (resolved == null) return

        // El escaneo todavía no alerta: primero se mide en dispositivo.
        if (BuildConfig.DEBUG && diagnosticModeEnabled()) {
            android.util.Log.i("SentinelA11y", "DIAG pantalla pkg=$pkg ${eventKey(resolved)} (SIN alerta)")
        }
    }

    private fun eventKey(resolved: ResolvedMatch): String =
        "${resolved.personId ?: "amb"}|${resolved.matchType.name}|${resolved.matchedValue}"

    private fun logMatch(pkg: String, via: String, resolved: ResolvedMatch?) {
        if (!BuildConfig.DEBUG) return
        android.util.Log.d(
            "SentinelA11y",
            "match $via pkg=$pkg -> " + (resolved?.let {
                "tipo=${it.matchType.name} persona=${it.personId ?: "amb"} ambiguo=${it.ambiguous} conf=${it.confidence}"
            } ?: "sin coincidencia"),
        )
    }

    private suspend fun deliver(pkg: String, resolved: ResolvedMatch, source: String, key: String) {
        runCatching {
            ConfirmedAlertProcessor.process(
                app = application as SentinelApp,
                resolved = resolved,
                source = source,
                eventKey = key,
                sourceApp = pkg,
                authorizedExcerpt = null,
            )
        }
    }

    /** SOLO DEBUG: deja la última pantalla leída para el visor de diagnóstico. */
    private fun dumpDiagnostic(pkg: String, nodes: List<String>, resolved: ResolvedMatch?) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            java.io.File(filesDir, DIAG_FILE).writeText(
                buildString {
                    appendLine("ts=${System.currentTimeMillis()}")
                    appendLine("pkg=$pkg")
                    appendLine("app=${AccessibilityScanPolicy.appLabel(pkg)}")
                    appendLine(
                        "match=" + (resolved?.let {
                            "${it.matchType.name}|${it.matchedValue}|${it.personId ?: "amb"}|conf=${it.confidence}"
                        } ?: "sin coincidencia"),
                    )
                    appendLine("--- nodos ---")
                    nodes.forEach { appendLine(it) }
                },
            )
        }
    }

    /** SOLO DEBUG: ¿está el archivo-bandera de diagnóstico? */
    private fun diagnosticModeEnabled(): Boolean =
        runCatching { java.io.File(filesDir, DIAG_FLAG).exists() }.getOrDefault(false)

    override fun onInterrupt() {}

    override fun onDestroy() {
        screenJob?.cancel()
        scope.cancel()
        persons = emptyList()
        super.onDestroy()
    }

    companion object {
        private const val SCREEN_SCAN_DELAY_MS = 1200L
        const val DIAG_FLAG = "a11y_diag"
        const val DIAG_FILE = "a11y_last_screen.txt"
    }
}
