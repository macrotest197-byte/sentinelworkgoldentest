package com.sentinel.recaudacion.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.recaudacion.ui.blacklist.BlacklistSummary
import com.sentinel.recaudacion.ui.blacklist.BlacklistViewModel
import com.sentinel.recaudacion.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    vm: BlacklistViewModel = viewModel(factory = BlacklistViewModel.Factory),
) {
    val summary by vm.summary.collectAsState()

    // Releer desde Room al reanudar: refleja aprovisionamientos aplicados fuera de la app
    // (escritura directa en SQLite) que el InvalidationTracker de Room no observa en vivo.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.sentinel.recaudacion.SentinelApp
    val telegramConfigured = androidx.compose.runtime.remember {
        app.telegramRepository.settings().isConfigured
    }
    val latest by app.incidentRepository.latest.collectAsState(initial = null)
    val lastEvent = latest?.let {
        val f = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
        "${f.format(java.util.Date(it.createdAt))} · ${it.matchType} (${it.source})"
    } ?: "ninguno"

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Text("  S.E.N.T.I.N.E.L.")
                }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(summary = summary, telegramConfigured = telegramConfigured, lastEvent = lastEvent)

            Text(
                "Secciones",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )

            val listaSubtitle = when (val s = summary) {
                is BlacklistSummary.Loading -> "Cargando…"
                is BlacklistSummary.Error -> "No se pudo leer la lista"
                is BlacklistSummary.Ready -> "${s.activas} activas · ${s.personas} personas · ${s.identificadores} identificadores"
            }
            SectionItem(Icons.Filled.Block, "Lista negra", listaSubtitle) {
                onOpen(Routes.BLACKLIST)
            }
            SectionItem(Icons.Outlined.ImportExport, "Exportar lista", "Copia de seguridad y plantilla CSV") {
                onOpen(Routes.IMPORT)
            }
            SectionItem(Icons.Filled.Send, "Telegram", "Destinos y prueba de envío") {
                onOpen(Routes.TELEGRAM)
            }
            // Pantallas de PRUEBA: solo en el build de pruebas (TEST_TOOLS), no en el corporativo.
            if (com.sentinel.recaudacion.BuildConfig.TEST_TOOLS) {
                SectionItem(Icons.Filled.Notifications, "Prueba de alarma", "Sonido y vibración") {
                    onOpen(Routes.ALARM)
                }
                SectionItem(Icons.Filled.Block, "Prueba de texto", "Simulación local sin envío de alertas") {
                    onOpen(Routes.TEXT_TEST)
                }
            }
            SectionItem(Icons.Filled.Keyboard, "Teclado de prevención", "Activación y alcance del IME") {
                onOpen(Routes.KEYBOARD)
            }
            SectionItem(Icons.Filled.Shield, "Mostrar alerta sobre otras apps", "Permiso para que el cartel aparezca encima") {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + context.packageName),
                        ),
                    )
                }
            }
            SectionItem(Icons.Filled.Shield, "Detección en billeteras", "Accesibilidad opt-in · solo aviso (experimental)") {
                onOpen(Routes.ACCESSIBILITY)
            }
            SectionItem(Icons.Filled.Shield, "Política de monitoreo", "Qué se lee, qué se envía y registro de aceptación") {
                onOpen(Routes.POLICY)
            }
            SectionItem(Icons.Outlined.History, "Historial", "Incidentes registrados") {
                onOpen(Routes.HISTORY)
            }
                // El visor lee el archivo que solo escribe el escaneo en debug; en el corporativo
                // no hay nada que mostrar, por eso la entrada queda oculta.
                if (com.sentinel.recaudacion.BuildConfig.TEST_TOOLS) {
                    SectionItem(Icons.Outlined.MonitorHeart, "Permisos y diagnóstico", "Estado real del servicio") {
                        onOpen(Routes.DIAGNOSTICS)
                    }
                }
            // Solo DEBUG: acceso a la prueba integral del circuito real. La Activity vive en
            // src/debug (no exportada) y no existe en el APK release; se lanza por nombre para
            // no crear dependencia de compilación con el sourceset de debug.
            if (com.sentinel.recaudacion.BuildConfig.DEBUG) {
                SectionItem(Icons.Filled.Warning, "Prueba real (debug)", "Circuito real: incidente + alarma + Telegram") {
                    runCatching {
                        context.startActivity(
                            android.content.Intent().setClassName(
                                context,
                                "com.sentinel.recaudacion.debug.RealAlertTestActivity",
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(summary: BlacklistSummary, telegramConfigured: Boolean, lastEvent: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    "  Monitoreo: REQUIERE ATENCIÓN",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "Todavía no hay una fuente de detección configurada (Fase 5). " +
                    "La lista negra, la alarma y Telegram se preparan en las fases previas.",
                style = MaterialTheme.typography.bodySmall,
            )
            // Estado explícito: nunca mostramos "0" como resultado definitivo mientras carga,
            // ni ocultamos un error como lista vacía.
            val listaTexto = when (summary) {
                is BlacklistSummary.Loading -> "Lista negra: cargando…"
                is BlacklistSummary.Error -> "Lista negra: ${summary.message}"
                is BlacklistSummary.Ready ->
                    "Lista negra: ${summary.activas} reglas activas de ${summary.total} · " +
                        "${summary.personas} personas · ${summary.identificadores} identificadores."
            }
            Text(
                listaTexto,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Conexión con Telegram: " + if (telegramConfigured) "configurada." else "sin configurar.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Último evento: $lastEvent.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 16.dp).fillMaxWidth(0.9f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
