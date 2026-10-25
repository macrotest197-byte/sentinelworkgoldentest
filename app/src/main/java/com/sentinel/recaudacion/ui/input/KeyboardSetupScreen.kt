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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Teclado SENTINEL") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Teclado de prevención (opt-in)", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cuando este teclado está seleccionado y la app dueña marca explícitamente un " +
                    "campo de destinatario como apto, SENTINEL compara lo que se escribe con la lista " +
                    "negra y, si coincide, avisa y limpia el dato (prevención) y registra el incidente."
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Alcance real y límites", style = MaterialTheme.typography.titleSmall)
                    Text("• Solo inspecciona campos que la app dueña marca para SENTINEL (fail-closed). " +
                        "Gboard y otros teclados no son observados; campos no marcados no se inspeccionan.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• Excluye contraseñas, PIN/OTP, autocompletado y tipos desconocidos, aunque estén marcados.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• No guarda el texto escrito ni registra teclas; solo evalúa la coincidencia en memoria.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• Mercado Pago, Ualá, Brubank y otras apps que NO integren el marcador de SENTINEL " +
                        "quedan fuera de alcance: ahí el teclado no puede prevenir ni detectar. Requieren " +
                        "integración de la app o un control previo a la confirmación.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Abrir ajustes de teclado de Android") }
            // Campo de demostración: solo en el build de pruebas, no en el corporativo.
            if (com.sentinel.recaudacion.BuildConfig.TEST_TOOLS) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(context, KeyboardPlaygroundActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Abrir campo de demostración (sin alarma/Telegram)") }
            }
            Text(
                "Para activarlo: elegí «SENTINEL · teclado de prevención» como teclado. Podés volver " +
                    "a tu teclado habitual desde los mismos ajustes.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
