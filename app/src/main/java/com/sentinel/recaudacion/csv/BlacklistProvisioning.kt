package com.sentinel.recaudacion.csv

import android.content.Context
import com.sentinel.recaudacion.data.repo.BlacklistRepository
import java.io.File

/** Administrator CSV intake; never reachable from the operator screens. */
object BlacklistProvisioning {
    const val FILE_NAME = "sentinel_blacklist_provision.csv"

    sealed interface Result {
        data object NotFound : Result
        data class Imported(val inserted: Int, val updated: Int) : Result
        data class Invalid(val reason: String) : Result
    }

    fun provisioningFile(context: Context): File =
        context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) } ?: File(context.filesDir, FILE_NAME)

    suspend fun importIfPresent(context: Context, repository: BlacklistRepository): Result {
        val file = provisioningFile(context)
        if (!file.exists()) return Result.NotFound
        val preview = runCatching { BlacklistCsv.preview(file.readText(Charsets.UTF_8)) }
            .getOrElse { return Result.Invalid("No se pudo leer el archivo de aprovisionamiento") }
        if (preview.valid.isEmpty() || preview.errors.isNotEmpty() || preview.duplicateCount > 0) {
            return Result.Invalid("CSV inválido: corregí errores o duplicados antes de aprovisionar")
        }
        val applied = repository.applyProvisionedRules(preview.valid.map { it.rule })
            ?: return Result.Invalid("CSV inválido o sin reglas aplicables")
        if (!file.delete()) return Result.Invalid("Reglas aplicadas, pero no se pudo borrar el archivo")
        return Result.Imported(applied.inserted, applied.updated)
    }
}
