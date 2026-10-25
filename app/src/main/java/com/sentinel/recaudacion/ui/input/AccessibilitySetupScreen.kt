package com.sentinel.recaudacion.ui.input

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.sentinel.recaudacion.detection.AccessibilityScanPolicy

/** Pantalla opt-in del servicio de accesibilidad: explica qué se analiza y abre los ajustes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isServiceEnabled(context)) }

    // Reevaluar el estado al volver de los ajustes de accesibilidad.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = isServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detección en billeteras") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (enabled) "Servicio ACTIVADO" else "Servicio desactivado",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text("Qué hace", style = MaterialTheme.typography.titleMedium)
            Text(
                "SOLO detección y aviso en estas billeteras autorizadas. Analiza únicamente el campo " +
                    "de destinatario para compararlo con la lista negra. NO bloquea transferencias, " +
                    "NO pulsa controles, NO lee contraseñas ni guarda pantallas. Si coincide, registra " +
                    "el incidente y avisa (alarma + Telegram) con el formato habitual.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Apps autorizadas:", style = MaterialTheme.typography.bodySmall)
            AccessibilityScanPolicy.ALLOWED_PACKAGES.forEach {
                Text("• ${AccessibilityScanPolicy.appLabel(it)}  ($it)", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Activación y desactivación voluntarias desde Ajustes → Accesibilidad → " +
                    "«SENTINEL · detección en billeteras».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (enabled) "Abrir ajustes para desactivar" else "Abrir ajustes para activar") }
        }
    }
}

/** ¿Está habilitado el servicio de accesibilidad de SENTINEL? (lectura de Settings.Secure) */
private fun isServiceEnabled(context: android.content.Context): Boolean {
    val expected = context.packageName + "/" +
        com.sentinel.recaudacion.input.SentinelAccessibilityService::class.java.name
    val enabledServices = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}
