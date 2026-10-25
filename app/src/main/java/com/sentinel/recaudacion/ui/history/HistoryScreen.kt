package com.sentinel.recaudacion.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.recaudacion.data.db.IncidentEntity
import com.sentinel.recaudacion.ui.common.FileIo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by vm.incidents.collectAsState()
    val query by vm.query.collectAsState()
    val severity by vm.severity.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            val csv = vm.exportCsv()
            val ok = FileIo.writeText(context, uri, csv)
            snackbar.showSnackbar(if (ok) "Historial exportado" else "No se pudo exportar")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { exportLauncher.launch("sentinel-historial.csv") },
                        enabled = items.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Exportar CSV")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQuery,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Buscar por valor, fuente o dispositivo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(severity == "", { vm.onSeverity("") }, label = { Text("Todas") })
                FilterChip(severity == "ADVERTENCIA", { vm.onSeverity("ADVERTENCIA") }, label = { Text("Advertencia") })
                FilterChip(severity == "CRITICA", { vm.onSeverity("CRITICA") }, label = { Text("Crítica") })
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin incidentes registrados todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(items, key = { it.id }) { inc -> IncidentCard(inc) }
                }
            }
        }
    }
}

private fun mask(value: String): String {
    val v = value.trim()
    if (v.length <= 4) return v
    return "${v.take(2)}${"•".repeat((v.length - 4).coerceAtLeast(1))}${v.takeLast(2)}"
}

@Composable
private fun IncidentCard(inc: IncidentEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(dateFmt.format(Date(inc.createdAt)), style = MaterialTheme.typography.labelMedium)
            inc.personName?.let { Text("Persona: $it", style = MaterialTheme.typography.titleMedium) }
            Text("${inc.matchType} · ${mask(inc.matchedValue)}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Text("Celular: ${inc.device}${inc.deviceId.takeIf(String::isNotBlank)?.let { " · ID ${it.take(8)}" }.orEmpty()} · Fuente: ${inc.source}", style = MaterialTheme.typography.bodySmall)
            inc.app?.let { Text("App: $it", style = MaterialTheme.typography.bodySmall) }
            inc.confidence?.let { Text("Confianza: ${"%.0f".format(it * 100)}%", style = MaterialTheme.typography.bodySmall) }
            inc.sourceExcerpt?.let { Text("Fragmento autorizado: $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                "Alarma: ${inc.alarmStatus} · Telegram: ${inc.telegramStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
