package com.sentinel.recaudacion.detection

import com.sentinel.recaudacion.data.model.BlacklistPerson

/**
 * Pipeline de análisis de un texto recibido de una **fuente autorizada y consentida**
 * (el operador selecciona/comparte el texto hacia SENTINEL). No lee apps de terceros en
 * segundo plano ni captura de forma oculta.
 *
 * Deduplica por evento: el mismo texto recibido varias veces no produce coincidencias
 * repetidas dentro de la ventana del deduplicador.
 */
object DetectionPipeline {

    data class Result(
        val matches: List<PersonMatch>,
        val duplicate: Boolean,
    ) {
        val hasMatch: Boolean get() = matches.isNotEmpty()
    }

    /**
     * @param eventKey clave de deduplicación; por defecto el texto normalizado para que el
     * mismo contenido no dispare coincidencias repetidas.
     */
    fun analyze(
        text: String,
        persons: List<BlacklistPerson>,
        dedup: EventDeduplicator,
        eventKey: String = TextNormalizer.normalizeText(text),
    ): Result {
        val matches = BlacklistPersonMatcher.match(text, persons)
        if (matches.isEmpty()) return Result(emptyList(), duplicate = false)
        val fresh = dedup.admit(eventKey)
        return Result(matches = if (fresh) matches else emptyList(), duplicate = !fresh)
    }
}
