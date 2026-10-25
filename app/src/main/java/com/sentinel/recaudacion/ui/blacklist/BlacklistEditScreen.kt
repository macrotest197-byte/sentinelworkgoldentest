package com.sentinel.recaudacion.ui.blacklist

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.recaudacion.data.model.BlacklistRule
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.data.model.Severity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistEditScreen(
    ruleId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: BlacklistViewModel = viewModel(factory = BlacklistViewModel.Factory),
) {
    val isEdit = ruleId != 0L
    var loaded by remember { mutableStateOf(!isEdit) }

    var type by remember { mutableStateOf(RuleType.ALIAS) }
    var value by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(Severity.CRITICA) }
    var exactMatch by remember { mutableStateOf(true) }
    var active by remember { mutableStateOf(true) }
    var valueError by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        if (isEdit) {
            vm.load(ruleId)?.let { r ->
                type = r.type
                value = r.value
                description = r.description.orEmpty()
                severity = r.severity
                exactMatch = r.exactMatch
                active = r.active
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Editar regla" else "Agregar regla") },
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
            TypeDropdown(type = type, onSelect = { type = it })

            OutlinedTextField(
                value = value,
                onValueChange = { value = it; valueError = false },
                label = { Text("Valor a detectar") },
                placeholder = { Text("Ej.: patodm.nx") },
                singleLine = true,
                isError = valueError,
                supportingText = {
                    if (valueError) Text("El valor no puede estar vacío")
                    else Text("Sin comillas ni formato especial. Se guarda como un registro.")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (type) {
                        RuleType.CBU, RuleType.CVU, RuleType.DOCUMENTO, RuleType.CUENTA -> KeyboardType.Number
                        else -> KeyboardType.Text
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Ej.: Cuenta restringida") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Severidad", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Severity.entries.forEach { s ->
                    FilterChip(
                        selected = severity == s,
                        onClick = { severity = s },
                        label = { Text(s.label) },
                    )
                }
            }

            OptionSwitch(
                title = "Coincidencia exacta",
                subtitle = if (exactMatch) "Debe coincidir el valor completo"
                else "Coincidencia parcial (el valor aparece dentro del texto)",
                checked = exactMatch,
                onChange = { exactMatch = it },
            )
            OptionSwitch(
                title = "Regla activa",
                subtitle = if (active) "Se evaluará en la detección" else "Guardada pero no se evalúa",
                checked = active,
                onChange = { active = it },
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (value.isBlank()) {
                            valueError = true
                            return@Button
                        }
                        vm.save(
                            BlacklistRule(
                                id = ruleId,
                                type = type,
                                value = value,
                                description = description,
                                severity = severity,
                                exactMatch = exactMatch,
                                active = active,
                            )
                        ) { ok -> if (ok) onDone() }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(type: RuleType, onSelect: (RuleType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = type.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de dato") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RuleType.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.label) },
                    onClick = { onSelect(t); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun OptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
