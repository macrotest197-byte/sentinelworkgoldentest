package com.sentinel.recaudacion.ui.blacklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.recaudacion.data.model.BlacklistRule
import com.sentinel.recaudacion.data.model.Severity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(
    onBack: () -> Unit,
    vm: BlacklistViewModel = viewModel(factory = BlacklistViewModel.Factory),
) {
    val rules by vm.rules.collectAsState()
    val query by vm.query.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista negra") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
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
            Text("Lista administrada por el responsable (solo lectura en este celular).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Buscar por valor, tipo o descripción") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            if (rules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "No hay reglas todavía."
                        else "Sin resultados para «$query».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun RuleCard(
    rule: BlacklistRule,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(rule.type.label) },
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
                SeverityChip(rule.severity)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(if (rule.active) "Activa" else "Inactiva", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                rule.value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (!rule.description.isNullOrBlank()) {
                Text(rule.description, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (rule.exactMatch) "Coincidencia exacta" else "Coincidencia parcial",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SeverityChip(severity: Severity) {
    val container = when (severity) {
        Severity.CRITICA -> MaterialTheme.colorScheme.error
        Severity.ADVERTENCIA -> MaterialTheme.colorScheme.tertiary
    }
    AssistChip(
        onClick = {},
        label = { Text(severity.label, color = Color.White) },
        colors = AssistChipDefaults.assistChipColors(containerColor = container),
    )
}
