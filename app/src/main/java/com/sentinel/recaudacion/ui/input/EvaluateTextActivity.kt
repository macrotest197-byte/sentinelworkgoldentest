package com.sentinel.recaudacion.ui.input

import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.data.settings.DeviceIdentitySnapshot
import com.sentinel.recaudacion.data.settings.DeviceRegistrationStatus
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.ResolvedMatch
import com.sentinel.recaudacion.telegram.AlertMessage
import com.sentinel.recaudacion.ui.theme.SentinelTheme

/**
 * Recibe texto que el operador **envía o selecciona explícitamente** hacia SENTINEL
 * (ACTION_PROCESS_TEXT desde el menú de selección, o "Compartir"). Entrada consentida y
 * acotada a la operación: NO hay lectura de fondo ni captura oculta.
 *
 * Muestra una **vista previa local** de la alerta que se generaría, usando el modelo real de
 * personas ([BlacklistPersonMatcher.resolve]), la identidad real del dispositivo
 * ([DeviceIdentityStore]) y el **mismo generador de mensajes** del circuito de alertas
 * ([AlertMessage.alert]). Es estrictamente local: NO crea incidentes, NO activa la alarma,
 * NO encola ni envía nada a Telegram. El procesamiento operativo real (incidente + alarma +
 * Telegram) se realiza únicamente por el circuito autorizado (ConfirmedAlertProcessor).
 */
class EvaluateTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = extractText(intent)
        val app = applicationContext as SentinelApp

        setContent {
            SentinelTheme {
                var state by remember { mutableStateOf<ResultState>(ResultState.Loading) }
                LaunchedEffect(text) {
                    state = if (text.isNullOrBlank()) {
                        ResultState.Empty
                    } else {
                        val persons = app.blacklistRepository.activePersons()
                        // Resolución pura: no toca el deduplicador real ni ningún efecto lateral.
                        val resolved = BlacklistPersonMatcher.resolve(text, persons)
                        if (resolved == null) {
                            ResultState.NoMatch
                        } else {
                            val identity = app.deviceIdentity.snapshot()
                            // «Le corresponde a»: persona real, o estado explícito (sin inventar).
                            val personDisplay = resolved.personName
                                ?: if (resolved.ambiguous) AlertMessage.AMBIGUOUS else AlertMessage.UNRESOLVED
                            // Mismo generador del circuito de alertas: no se duplica el formato.
                            val preview = AlertMessage.alert(
                                device = identity.friendlyName,
                                matchedValue = resolved.matchedValue,
                                personName = personDisplay,
                                typeLabel = resolved.matchType.label,
                                wallet = "Texto compartido",
                            )
                            ResultState.Match(resolved, identity, preview)
                        }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EvaluateScreen(state)
                }
            }
        }
    }

    private fun extractText(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND ->
            intent.getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }
}

private sealed interface ResultState {
    data object Loading : ResultState
    data object Empty : ResultState
    data object NoMatch : ResultState
    data class Match(
        val resolved: ResolvedMatch,
        val identity: DeviceIdentitySnapshot,
        val preview: String,
    ) : ResultState
}

@Composable
private fun EvaluateScreen(state: ResultState) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SENTINEL — Verificación de texto", style = MaterialTheme.typography.titleLarge)
        Text(
            "Verificación consentida: analizás el texto que vos enviaste o seleccionaste. " +
                "Vista previa local: no se crea incidente, no se activa la alarma ni se envía Telegram.",
            style = MaterialTheme.typography.bodySmall,
        )
        when (state) {
            ResultState.Loading -> Text("Analizando…")
            ResultState.Empty -> Text("No se recibió texto para analizar.")
            ResultState.NoMatch -> Card(Modifier.fillMaxWidth()) {
                Text("Sin coincidencias con la lista negra.", Modifier.padding(16.dp))
            }
            is ResultState.Match -> AlertPreviewCard(state)
        }
    }
}

@Composable
private fun AlertPreviewCard(match: ResultState.Match) {
    val r = match.resolved
    val id = match.identity
    val personLine = when {
        r.personName != null -> r.personName
        r.ambiguous -> AlertMessage.AMBIGUOUS
        else -> AlertMessage.UNRESOLVED
    }
    val deviceUnregistered = id.status == DeviceRegistrationStatus.PENDIENTE
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vista previa de alerta (local — no enviada)", style = MaterialTheme.typography.titleMedium)
            // Mensaje generado por AlertMessage.alert (mismo texto que enviaría el circuito real).
            Text(match.preview, style = MaterialTheme.typography.bodyMedium)
            // Estados explícitos cuando falta información real (no se inventa nada).
            if (deviceUnregistered) {
                Text(
                    "⚠ Dispositivo sin registrar: se usa el nombre provisional «${id.friendlyName}». " +
                        "Registralo con el flujo de administrador.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (r.personName == null) {
                val detalle = if (r.ambiguous)
                    "El identificador corresponde a más de una persona; no se elige ninguna."
                else
                    "El identificador no está asociado a ninguna persona registrada."
                Text("⚠ $detalle", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Persona: $personLine · Confianza: ${"%.0f".format(r.confidence * 100)}% · " +
                    "Dispositivo: ${id.friendlyName}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
