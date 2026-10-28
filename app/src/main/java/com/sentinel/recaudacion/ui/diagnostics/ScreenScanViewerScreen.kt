package com.sentinel.recaudacion.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Visor de diagnóstico: muestra el texto CRUDO que el escáner de pantalla leyó, nodo por nodo,
 * y qué habría matcheado. Solo lectura de un archivo local; no alerta ni envía nada.
 *
 * Es la herramienta de medición de la Fase 3: sirve para ver qué expone cada billetera antes de
 * habilitar el escaneo en producción.
 */
@Composable
fun ScreenScanViewerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var dump by remember { mutableStateOf<Dump?>(null) }
    var flagOn by remember { mutableStateOf(false) }
    var serviceOn by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        while (true) {
            dump = readDump(context)
            flagOn = diagFlag(context).exists()
            serviceOn = serviceEnabled(context)
            delay(2_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Visor de escaneo", style = MaterialTheme.typography.titleLarge)
        Text(
            "Texto que el escáner leyó de la última pantalla de billetera. Se actualiza solo cada 2 segundos.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Estado("Bandera", flagOn)
            Estado("Servicio", serviceOn)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { toggleDiagFlag(context); reload++ }) {
                Text(if (flagOn) "Quitar bandera" else "Crear bandera")
            }
            OutlinedButton(onClick = { diagFlag(context).delete(); dump = null }) { Text("Borrar") }
        }

        val current = dump
        if (current == null) {
            Text("Sin capturas todavía. Activá la bandera, abrí una billetera y llegá a la pantalla de destinatario.", style = MaterialTheme.typography.bodySmall)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("App: ${current.app}", style = MaterialTheme.typography.bodyMedium)
                    Text("Hora: ${current.time}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (current.match.startsWith("sin")) "Match: sin coincidencia" else "Match: ${current.match}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OutlinedButton(onClick = { copy(context, current.raw) }) { Text("Copiar todo") }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(current.nodes) { node ->
                    Text(node, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun Estado(label: String, on: Boolean) {
    Text(
        "$label: ${if (on) "sí" else "no"}",
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

data class Dump(val time: String, val app: String, val match: String, val nodes: List<String>, val raw: String)

private const val DIAG_FLAG = "a11y_diag"
private const val DIAG_FILE = "a11y_last_screen.txt"

private fun diagFlag(context: Context) = File(context.filesDir, DIAG_FLAG)

private fun toggleDiagFlag(context: Context) {
    val f = diagFlag(context)
    runCatching { if (f.exists()) f.delete() else f.createNewFile() }
}

private fun readDump(context: Context): Dump? {
    val f = File(context.filesDir, DIAG_FILE)
    if (!f.exists()) return null
    val raw = runCatching { f.readText() }.getOrNull() ?: return null
    val lines = raw.lines()
    fun header(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix).orEmpty()
    val ts = header("ts=").toLongOrNull() ?: 0L
    val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
    val sep = lines.indexOfFirst { it.startsWith("--- nodos ---") }
    val nodes = if (sep >= 0) lines.drop(sep + 1).filter { it.isNotBlank() } else emptyList()
    return Dump(time = time, app = header("app=").ifBlank { header("pkg=") }, match = header("match=").ifBlank { "sin coincidencia" }, nodes = nodes, raw = raw)
}

private fun serviceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabled.contains(context.packageName + "/")
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("SENTINEL", text))
}
