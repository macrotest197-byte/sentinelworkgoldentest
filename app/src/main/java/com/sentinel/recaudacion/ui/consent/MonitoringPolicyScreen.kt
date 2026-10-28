package com.sentinel.recaudacion.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.recaudacion.detection.MonitoringPolicy

/**
 * Política de monitoreo. En modo [onboarding] se muestra antes de usar la app y pide aceptación
 * explícita; en modo consulta se abre desde Inicio y solo se lee.
 */
@Composable
fun MonitoringPolicyScreen(
    onboarding: Boolean,
    onAccept: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            if (onboarding) "Antes de empezar" else "Qué monitorea este teléfono",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Política versión ${MonitoringPolicy.VERSION}. Si cambia, se vuelve a pedir aceptación.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(MonitoringPolicy.SECTIONS) { section ->
                Text(section.title, style = MaterialTheme.typography.titleSmall)
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
            }
        }

        if (onboarding) {
            Text(
                "Al aceptar queda registrado en este teléfono la fecha y el identificador de instalación.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("Acepto y continúo")
            }
        } else {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Volver")
            }
        }
    }
}