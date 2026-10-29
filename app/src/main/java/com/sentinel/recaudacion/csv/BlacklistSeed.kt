package com.sentinel.recaudacion.csv

import android.content.Context
import com.sentinel.recaudacion.data.repo.BlacklistRepository

/**
 * Siembra inicial de la lista negra desde un asset del APK.
 *
 * Corre una sola vez por instalación: deja una marca en preferencias y no vuelve a tocar la base.
 * Así los cambios posteriores (aprovisionamiento por archivo o ediciones futuras) no se pisan al
 * reiniciar la app. No borra nada, solo inserta.
 */
object BlacklistSeed {

    private const val ASSET = "lista_inicial.csv"
    private const val PREF = "sentinel_seed"
    private const val KEY_DONE = "lista_inicial_sembrada"

    /** true si sembró en esta llamada. */
    suspend fun seedIfNeeded(context: Context, repository: BlacklistRepository): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return false
        val text = runCatching {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return false
        val preview = runCatching { BlacklistCsv.preview(text) }.getOrNull() ?: return false
        if (preview.valid.isEmpty() || preview.errors.isNotEmpty()) return false
        repository.applyProvisionedRules(preview.valid.map { it.rule }) ?: return false
        prefs.edit().putBoolean(KEY_DONE, true).apply()
        return true
    }
}