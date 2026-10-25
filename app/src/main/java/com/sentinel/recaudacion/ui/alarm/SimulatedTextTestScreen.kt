package com.sentinel.recaudacion.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.TextNormalizer
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SimulatedTextTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SentinelApp
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<List<Pair<String, Double>>?>(null) }
    val compare: () -> Unit = {
        scope.launch {
            result = BlacklistPersonMatcher.match(input, app.blacklistRepository.activePersons())
                .map { it.primaryName to it.confidence }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Prueba de texto simulada") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pegá o escribí un texto de prueba. SENTINEL solo compara con la lista en esta pantalla; no monitorea otras apps ni envía alertas a Telegram.")
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; result = null },
                label = { Text("Texto de prueba") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { compare() }),
            )
            Button(onClick = compare, enabled = input.isNotBlank()) { Text("Comparar con lista negra") }
            result?.let { matches ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (matches.isEmpty()) "Sin coincidencias" else "Coincidencias de prueba", style = MaterialTheme.typography.titleMedium)
                        matches.forEach { (name, confidence) ->
                            Text("$name · ${"%.0f".format(confidence * 100)}% de confianza")
                        }
                        Text("Coincidencias desde ${"%.0f".format(TextNormalizer.MIN_CONFIDENCE * 100)}%. Prueba local: no se creó incidente ni se envió Telegram.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
