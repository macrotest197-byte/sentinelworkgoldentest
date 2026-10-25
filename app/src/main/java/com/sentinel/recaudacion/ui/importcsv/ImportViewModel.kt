package com.sentinel.recaudacion.ui.importcsv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.csv.BlacklistCsv
import com.sentinel.recaudacion.csv.BlacklistImportPreview
import com.sentinel.recaudacion.data.repo.BlacklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImportUiState(
    val preview: BlacklistImportPreview? = null,
    val status: String? = null,
    val busy: Boolean = false,
)

class ImportViewModel(private val repo: BlacklistRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ImportUiState())
    val ui: StateFlow<ImportUiState> = _ui.asStateFlow()

    fun loadPreview(csvText: String) {
        val preview = BlacklistCsv.preview(csvText)
        val msg = "Vista previa: ${preview.newCount} nuevas, " +
            "${preview.duplicateCount} duplicadas en el archivo, ${preview.errorCount} con error."
        _ui.value = ImportUiState(preview = preview, status = msg)
    }

    fun clearPreview() { _ui.value = ImportUiState() }

    /** Importa sólo las filas válidas no duplicadas dentro del archivo, omitiendo las ya existentes. */
    fun confirmImport(onDone: () -> Unit) {
        val preview = _ui.value.preview ?: return
        val toImport = preview.valid.filter { !it.duplicateInFile }.map { it.rule }
        if (toImport.isEmpty()) {
            _ui.value = _ui.value.copy(status = "No hay filas nuevas para importar.")
            return
        }
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            val result = repo.importRules(toImport, skipDbDuplicates = true)
            _ui.value = ImportUiState(
                status = "Importación: ${result.inserted} agregadas, " +
                    "${result.skippedDuplicates} omitidas (ya existían)."
            )
            onDone()
        }
    }

    suspend fun exportBackup(): String = BlacklistCsv.export(repo.allRules())

    fun template(): String = BlacklistCsv.template()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SentinelApp
                ImportViewModel(app.blacklistRepository)
            }
        }
    }
}
