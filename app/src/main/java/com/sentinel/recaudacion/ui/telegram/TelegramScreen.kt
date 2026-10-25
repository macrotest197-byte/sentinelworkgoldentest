package com.sentinel.recaudacion.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(
    onBack: () -> Unit,
    vm: TelegramViewModel = viewModel(factory = TelegramViewModel.Factory),
) {
    val ui by vm.ui.collectAsState()
    val pending by vm.pendingCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Estado de la configuración autorizada (solo lectura).
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (ui.configured) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Text(
                            "  Configuración autorizada (solo lectura)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        "Estado: " + if (ui.configured) "configurado" else "sin configurar",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Token: ${ui.maskedToken}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Destinos autorizados: " +
                            if (ui.destinations.isEmpty()) "ninguno" else ui.destinations.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (pending > 0) {
                        Text(
                            "En cola (pendientes): $pending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            ui.status?.let { s ->
                Card(Modifier.fillMaxWidth()) {
                    Text(s, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = { vm.sendTest() },
                enabled = ui.configured && !ui.sending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.sending) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                }
                Text("Enviar notificación de prueba")
            }

            HorizontalDivider()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cómo se configura (administrador)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "El token del bot y los destinos no se editan desde esta pantalla. Los " +
                            "configura el administrador colocando un archivo de aprovisionamiento en el " +
                            "dispositivo; SENTINEL lo importa cifrado y lo borra. Ver INFORME.md.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "El operador no puede cambiar el bot ni agregar destinos. La alarma local " +
                            "funciona aunque Telegram no tenga conexión.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
