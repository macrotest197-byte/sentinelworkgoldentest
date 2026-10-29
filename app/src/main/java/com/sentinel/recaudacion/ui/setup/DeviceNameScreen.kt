package com.sentinel.recaudacion.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Nombre mínimo y máximo admitidos. El nombre viaja en cada alerta. */
private const val MIN_LEN = 3
private const val MAX_LEN = 40

/**
 * Alta del celular en el primer arranque. Pide un nombre visible que identifica al equipo en las
 * alertas de Telegram. Se muestra antes del consentimiento: sin nombre no se sigue.
 */
@Composable
fun DeviceNameScreen(onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val clean = name.trim()
    val valid = clean.length in MIN_LEN..MAX_LEN

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Nombre de este celular", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Poné el nombre con el que se va a identificar este equipo en las alertas, " +
                "por ejemplo el nombre del operador o el puesto. Va a aparecer en cada aviso de Telegram.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= MAX_LEN) name = it },
            label = { Text("Nombre del celular") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Text(
            if (clean.isNotEmpty() && !valid) "Entre $MIN_LEN y $MAX_LEN caracteres." else " ",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { onSave(clean) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Guardar y continuar")
        }
    }
}