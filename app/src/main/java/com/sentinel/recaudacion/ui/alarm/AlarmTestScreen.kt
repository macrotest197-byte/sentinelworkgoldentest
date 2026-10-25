package com.sentinel.recaudacion.ui.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.alarm.AlarmController
import com.sentinel.recaudacion.alarm.AlarmService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active by AlarmController.active.collectAsState()

    fun logIncident() {
        val repo = (context.applicationContext as SentinelApp).incidentRepository
        val app = context.applicationContext as SentinelApp
        scope.launch { repo.logManualTest(device = app.deviceIdentity.friendlyName, deviceId = app.deviceIdentity.id) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AlarmService.start(context, "Prueba de alarma local (sin motor de detección).")
            logIncident()
        }
    }

    fun launchTest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        AlarmService.start(context, "Prueba de alarma local (sin motor de detección).")
        logIncident()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prueba de alarma") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (active) "Alarma ACTIVA" else "Alarma detenida",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Text(
                "Esta prueba activa la alarma local (sonido en bucle + vibración intensa) " +
                    "y muestra la pantalla de alerta a pantalla completa, sin necesitar todavía " +
                    "el motor de detección. Reconocé la alerta para detenerla.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = { launchTest() },
                enabled = !active,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Text("  Probar alarma", modifier = Modifier.padding(8.dp))
            }

            OutlinedButton(
                onClick = { AlarmService.stop(context) },
                enabled = active,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Text("  Detener alarma", modifier = Modifier.padding(8.dp))
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Limitaciones reales", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "• En modo No molestar (DND), el sonido puede quedar silenciado salvo que " +
                            "se conceda a SENTINEL la excepción de No molestar en los ajustes de Android. " +
                            "No se puede forzar universalmente.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "• La intensidad y duración de la vibración dependen del hardware del equipo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "• En Android 12+, un servicio en primer plano no puede iniciarse desde " +
                            "segundo plano salvo excepciones; por eso la prueba se lanza con la app abierta.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
