package com.sentinel.recaudacion.input

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.sentinel.recaudacion.BuildConfig
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.settings.ConsentStore
import com.sentinel.recaudacion.data.settings.DeviceIdentityStore
import com.sentinel.recaudacion.detection.AccessibilityScanPolicy
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.ConfirmedAlertProcessor
import com.sentinel.recaudacion.detection.PersonScopeFilter
import com.sentinel.recaudacion.detection.ResolvedMatch
import com.sentinel.recaudacion.detection.ScanRoute
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
 * Servicio de accesibilidad opt-in, acotado a billeteras autorizadas. Detecta y avisa, NUNCA
 * bloquea.
 *
 * Dos fuentes, un solo circuito: el campo que cambió (vía exacta) y la pantalla completa con
 * retardo (red de seguridad para el titular que la billetera resuelve después).
 *
 * Cada vía tiene su propia lista: [PersonScopeFilter] decide qué personas participan y cuáles
 * quedan exceptuadas en este dispositivo.
 *
 * La clave de evento lleva un número de sesión que sube en cada cambio de ventana: dentro de una
 * misma pantalla alias/CBU/nombre de la misma persona se colapsan en una sola alerta, pero al
 * cambiar de pantalla, de billetera o al reabrir la app la clave es nueva y vuelve a alertar.
 */
class SentinelAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var campoPersons: List<BlacklistPerson> = emptyList()
    @Volatile private var pantallaPersons: List<BlacklistPerson> = emptyList()
    private val identity by lazy { DeviceIdentityStore(applicationContext) }
    private val consent by lazy { ConsentStore(applicationContext) }
    @Volatile private var lastKey: String? = null
    @Volatile private var lastScreenKey: String? = null
    private var screenJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { refreshPersons() }
    }

    private suspend fun refreshPersons() {
        runCatching {
            val all = (application as SentinelApp).blacklistRepository.activePersons()
            val device = identity.friendlyName
            campoPersons = PersonScopeFilter.filter(all, ScanRoute.CAMPO, device)
            pantallaPersons = PersonScopeFilter.filter(all, ScanRoute.PANTALLA, device)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (!consent.isAccepted()) return
        val pkg = e.packageName?.toString()
        if (pkg == null || pkg !in AccessibilityScanPolicy.ALLOWED_PACKAGES) return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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

        inspectField(pkg, e)
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

    private fun inspect(pkg: String, text: String) {
        scope.launch {
            if (campoPersons.isEmpty()) refreshPersons()
            val resolved = BlacklistPersonMatcher.resolve(text, campoPersons)
            logMatch(pkg, "campo", resolved)
            if (resolved == null) return@launch
            val key = eventKey(pkg, resolved)
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

    private suspend fun scanScreen(pkg: String) {
        val nodes = runCatching { ScreenTextCollector.collect(rootInActiveWindow) }.getOrDefault(emptyList())
        if (nodes.isEmpty()) return
        val text = nodes.joinToString(" ")
        if (!ScreenScanPolicy.shouldScan(pkg, text)) return

        val screenKey = ScreenScanPolicy.screenKey(text)
        if (screenKey == lastScreenKey) return
        lastScreenKey = screenKey

        if (pantallaPersons.isEmpty()) refreshPersons()
        val resolved = BlacklistPersonMatcher.resolve(text, pantallaPersons, TextNormalizer.MatchScope.PANTALLA)
        dumpDiagnostic(pkg, nodes, resolved)
        logMatch(pkg, "pantalla", resolved)
        if (resolved == null) return
        if (!ScreenScanPolicy.shouldAlert(resolved.matchType, resolved.confidence)) return

        val key = eventKey(pkg, resolved)
        if (key == lastKey) return
        lastKey = key
        if (BuildConfig.DEBUG && diagnosticModeEnabled()) {
            android.util.Log.i("SentinelA11y", "DIAG pantalla pkg=$pkg $key (SIN alerta)")
            return
        }
        deliver(pkg, resolved, "Accesibilidad · pantalla · ${AccessibilityScanPolicy.appLabel(pkg)}", key)
    }

    private fun eventKey(pkg: String, resolved: ResolvedMatch): String {
        val person = resolved.personId ?: "amb|${resolved.matchType.name}|${resolved.matchedValue}"
        return "$pkg|$person"
    }

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

    private fun dumpDiagnostic(pkg: String, nodes: List<String>, resolved: ResolvedMatch?) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            java.io.File(filesDir, DIAG_FILE).writeText(
                buildString {
                    appendLine("ts=${System.currentTimeMillis()}")
                    appendLine("pkg=$pkg")
                    appendLine("app=${AccessibilityScanPolicy.appLabel(pkg)}")
                    appendLine("device=${identity.friendlyName}")
                    appendLine("personas campo=${campoPersons.size} pantalla=${pantallaPersons.size}")
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

    private fun diagnosticModeEnabled(): Boolean =
        runCatching { java.io.File(filesDir, DIAG_FLAG).exists() }.getOrDefault(false)

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        com.sentinel.recaudacion.work.GuardScheduler.checkNow(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        screenJob?.cancel()
        scope.cancel()
        campoPersons = emptyList()
        pantallaPersons = emptyList()
        super.onDestroy()
    }

    companion object {
        private const val SCREEN_SCAN_DELAY_MS = 1200L
        const val DIAG_FLAG = "a11y_diag"
        const val DIAG_FILE = "a11y_last_screen.txt"
    }
}
