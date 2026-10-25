package com.sentinel.recaudacion.ui.importcsv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.recaudacion.ui.common.FileIo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    vm: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            val ok = FileIo.writeText(context, uri, vm.exportBackup())
            snackbar.showSnackbar(if (ok) "Copia de seguridad exportada" else "No se pudo exportar")
        }
    }
    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val ok = FileIo.writeText(context, uri, vm.template())
            scope.launch { snackbar.showSnackbar(if (ok) "Plantilla guardada" else "No se pudo guardar") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar lista") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Lista negra administrada", style = MaterialTheme.typography.titleMedium)
            Text(
                "El administrador aplica cambios con un CSV de aprovisionamiento privado. " +
                    "Esta pantalla solo permite exportar una copia; el operador no puede importar cambios.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text("Exportar", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { exportLauncher.launch("sentinel-lista-negra.csv") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Exportar copia de seguridad")
            }
            OutlinedButton(
                onClick = { templateLauncher.launch("plantilla-lista-negra.csv") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Descargar plantilla CSV")
            }
        }
    }
}

