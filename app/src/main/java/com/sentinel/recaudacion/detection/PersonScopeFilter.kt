package com.sentinel.recaudacion.detection

import com.sentinel.recaudacion.data.model.BlacklistPerson

/** Vía por la que se puede detectar a una persona. */
enum class ScanRoute { CAMPO, PANTALLA }

/**
 * Decide qué personas participan de cada vía y en qué dispositivos quedan exceptuadas.
 * Puro: no toca Android ni la base, así que se puede probar entero.
 *
 * Vacío significa el comportamiento histórico: ambas vías, sin excepciones.
 */
object PersonScopeFilter {

    /** Interpreta la columna `escaneo`. Vacío o ilegible = ambas vías. */
    fun parseRoutes(raw: String?): Set<ScanRoute> {
        val tokens = raw.orEmpty()
            .split(',', '|', '+')
            .map { TextNormalizer.normalizeText(it) }
            .filter(String::isNotBlank)
        if (tokens.isEmpty()) return ScanRoute.entries.toSet()

        val routes = mutableSetOf<ScanRoute>()
        var ninguno = false
        for (t in tokens) {
            when {
                t.startsWith("amb") || t.startsWith("todo") -> return ScanRoute.entries.toSet()
                t.startsWith("campo") -> routes += ScanRoute.CAMPO
                t.startsWith("pantalla") -> routes += ScanRoute.PANTALLA
                t.startsWith("ningun") || t == "no" -> ninguno = true
            }
        }
        return when {
            routes.isNotEmpty() -> routes
            ninguno -> emptySet()
            else -> ScanRoute.entries.toSet()
        }
    }

    /** Interpreta la columna `excepto_en_dispositivos`: nombres separados por `|`. */
    fun parseDevices(raw: String?): List<String> = raw.orEmpty()
        .split('|')
        .map(String::trim)
        .filter(String::isNotBlank)

    /** ¿Esta persona está exceptuada en este dispositivo? Comparación sin mayúsculas ni acentos. */
    fun isExemptOn(person: BlacklistPerson, deviceName: String?): Boolean {
        val device = TextNormalizer.normalizeText(deviceName.orEmpty())
        if (device.isEmpty()) return false
        return person.exemptDevices.any { TextNormalizer.normalizeText(it) == device }
    }

    /** Personas que participan de [route] en este dispositivo. */
    fun filter(
        persons: List<BlacklistPerson>,
        route: ScanRoute,
        deviceName: String?,
    ): List<BlacklistPerson> =
        persons.filter { route in it.scanRoutes && !isExemptOn(it, deviceName) }
}