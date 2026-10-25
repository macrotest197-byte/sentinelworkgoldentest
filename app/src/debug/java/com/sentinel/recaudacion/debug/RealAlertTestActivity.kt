package com.sentinel.recaudacion.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sentinel.recaudacion.BuildConfig
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.ConfirmedAlertProcessor
import com.sentinel.recaudacion.ui.theme.SentinelTheme
import kotlinx.coroutines.launch

/**
 * SOLO DEBUG — prueba integral del CIRCUITO REAL, disparada desde un botón dentro de la app
 * con confirmación explícita. NO es exportada (no puede abrirse desde otras apps ni por adb);
 * sólo se accede desde el botón debug de la pantalla principal. NO existe en el APK release.
 *
 * Recorre el mismo camino de producción que el teclado SENTINEL (sin pipeline paralelo):
 *   matcher real → resolución de persona → identidad `Celular Pato` → deduplicación →
 *   historial → alarma → cola de Telegram → envío (ConfirmedAlertProcessor real).
 *
 * Diseñada para ejecutarse UNA sola vez: el botón se bloquea tras la primera ejecución.
 * El texto de prueba por defecto es la regla existente `patodm.nx` (no se altera la lista).
 */
class RealAlertTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RealAlertTestScreen()
                }
            }
        }
    }
}

private sealed interface RunState {
    data object Idle : RunState
    data object Running : RunState
    data class Done(val summary: String) : RunState
    data class Skipped(val reason: String) : RunState
}

@Composable
private fun RealAlertTestScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SentinelApp
    val scope = rememberCoroutineScope()
    val textoPrueba = "patodm.nx"
    var state by remember { mutableStateOf<RunState>(RunState.Idle) }
    var showConfirm by remember { mutableStateOf(false) }
    // Guardia de ejecución única: una vez disparado, no se puede volver a ejecutar en esta sesión.
    var alreadyFired by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("PRUEBA REAL (solo debug)", style = MaterialTheme.typography.titleLarge)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⚠ Esta acción recorre el circuito REAL:", style = MaterialTheme.typography.titleMedium)
                Text("• Crea un incidente en el historial.")
                Text("• Activa la alarma local del dispositivo.")
                Text("• Encola y ENVÍA una alerta real al grupo de Telegram configurado.")
                Text("Texto de prueba: «$textoPrueba» (regla existente). Dispositivo: ${app.deviceIdentity.friendlyName}.")
            }
        }

        Button(
            onClick = { showConfirm = true },
            enabled = BuildConfig.DEBUG && !alreadyFired && state != RunState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (alreadyFired) "Prueba ya ejecutada" else "Ejecutar prueba real (una sola vez)") }

        when (val s = state) {
            RunState.Idle -> {}
            RunState.Running -> Text("Ejecutando circuito real… (alarma + envío)")
            is RunState.Done -> Card(Modifier.fillMaxWidth()) {
                Text(s.summary, Modifier.padding(16.dp))
            }
            is RunState.Skipped -> Card(Modifier.fillMaxWidth()) {
                Text("No ejecutada: ${s.reason}", Modifier.padding(16.dp))
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirmar prueba real") },
            text = {
                Text(
                    "Se enviará UNA alerta real al grupo de Telegram y se activará la alarma local, " +
                        "una sola vez. ¿Confirmás?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    if (alreadyFired) return@TextButton
                    alreadyFired = true
                    state = RunState.Running
                    scope.launch {
                        state = runRealCircuitOnce(app, textoPrueba)
                    }
                }) { Text("Confirmar y enviar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}

/** Invoca el ConfirmedAlertProcessor real una única vez y resume el resultado. */
private suspend fun runRealCircuitOnce(app: SentinelApp, texto: String): RunState {
    val persons = app.blacklistRepository.activePersons()
    val resolved = BlacklistPersonMatcher.resolve(texto, persons)
        ?: return RunState.Skipped("sin coincidencia en la lista real para «$texto».")
    val key = "${resolved.personId ?: "amb"}|${resolved.matchType.name}|${resolved.matchedValue}"
    val result = ConfirmedAlertProcessor.process(
        app = app,
        resolved = resolved,
        source = "PRUEBA INTEGRAL · botón debug",
        eventKey = key,
        sourceApp = "prueba.integral.debug",
        authorizedExcerpt = null,
    ) ?: return RunState.Skipped("el procesador no admitió el evento (dedup o confianza baja).")
    return RunState.Done(
        "Incidente #${result.incidentId}\n" +
            "Persona: ${resolved.personName} · Valor: ${resolved.matchedValue} · Tipo: ${resolved.matchType.label}\n" +
            "Alarma: ${result.alarmStatus}\n" +
            "Telegram (cola): ${result.telegramStatus}\n" +
            "El estado final de envío se confirma en la pantalla de Telegram / historial.",
    )
}
