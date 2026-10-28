package com.sentinel.recaudacion.detection

import com.sentinel.recaudacion.data.model.RuleType
import java.text.Normalizer

/**
 * Normalización controlada para la comparación de la lista negra.
 *
 * Reglas:
 *  - Alias, nombre, código, cuenta, otro: minúsculas, se quitan acentos y se
 *    colapsan los espacios.
 *  - CBU, CVU, documento: se conservan únicamente los dígitos. NO se "corrigen"
 *    ni reordenan los números; sólo se eliminan separadores.
 *
 * NOMBRES (cambio de dirección): la lista guarda el nombre legal completo y la billetera
 * puede mostrar una versión PARCIAL del mismo (lo permite mientras sea compatible con el
 * documento). Por eso el match acepta que el texto observado sea más corto que el guardado,
 * más largo, con iniciales, con el nombre de pila abreviado o en orden invertido.
 *
 * Los vecinos pasan a ser una PREFERENCIA, no un requisito: en una pantalla completa el
 * nombre aparece rodeado de palabras que no están en [SENTENCE_WORDS] y exigirlas
 * descartaba coincidencias legítimas.
 */
object TextNormalizer {

    const val MIN_CONFIDENCE = 0.95

    /**
     * Ámbito de la comparación. No cambia la normalización: cambia qué reglas de nombre se
     * habilitan. El token único es una red ancha y solo se usa al leer una pantalla completa.
     */
    enum class MatchScope { CAMPO, PANTALLA }

    /** Reglas ampliadas. Habilitadas por decisión del proyecto: más cobertura, más ruido. */
    private const val ALLOW_REVERSED_ORDER = true
    private const val ALLOW_ABBREVIATED_FIRST_NAME = true
    private const val ALLOW_WIDE_SPAN = true

    data class Match(val confidence: Double, val matchedText: String)

    fun normalizeText(input: String): String {
        val noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    fun normalizeNumeric(input: String): String =
        input.filter { it.isDigit() }

    /**
     * Canoniza un teléfono argentino para comparar formatos distintos del MISMO número.
     * Limitación conocida (prototipo): el prefijo 15 NO se unifica.
     */
    fun normalizePhone(input: String): String {
        var d = input.filter { it.isDigit() }
        if (d.length > 10 && d.startsWith("54")) d = d.substring(2)
        if (d.startsWith("0")) d = d.substring(1)
        if (d.length == 11 && d.startsWith("9")) d = d.substring(1)
        return d
    }

    fun normalizeForType(type: RuleType, value: String): String =
        when (type) {
            RuleType.CBU, RuleType.CVU, RuleType.DOCUMENTO, RuleType.CUENTA ->
                normalizeNumeric(value).ifEmpty { normalizeText(value) }
            RuleType.TELEFONO -> normalizePhone(value).ifEmpty { normalizeText(value) }
            else -> normalizeText(value)
        }

    fun matches(
        type: RuleType,
        stored: String,
        observed: String,
        scope: MatchScope = MatchScope.CAMPO,
    ): Boolean = match(type, stored, observed, scope)?.confidence?.let { it >= MIN_CONFIDENCE } == true

    /** Returns a conservative confidence for one rule found in the supplied text. */
    fun match(
        type: RuleType,
        stored: String,
        observed: String,
        scope: MatchScope = MatchScope.CAMPO,
    ): Match? {
        val expected = normalizeForType(type, stored)
        val actual = normalizeForType(type, observed)
        if (expected.isEmpty() || actual.isEmpty()) return null
        if (type == RuleType.TELEFONO) {
            val target = normalizePhone(stored)
            if (target.length < 8) return null
            val found = Regex("[0-9][0-9 ()+.\\-]*[0-9]|[0-9]").findAll(observed)
                .map { normalizePhone(it.value) }
                .any { it == target }
            return if (found) Match(1.0, stored) else null
        }
        if (type in setOf(RuleType.CBU, RuleType.CVU, RuleType.DOCUMENTO, RuleType.CUENTA)) {
            if (expected.length < 6) return null
            return if (actual == expected) Match(1.0, stored)
            else if (actual.contains(expected)) Match(0.99, stored) else null
        }
        if (expected == actual) return Match(1.0, stored)
        if (type != RuleType.NOMBRE) {
            val significantLength = expected.count { it.isLetterOrDigit() }
            return if (significantLength >= 5 && tokenSequencePresent(expected, actual)) Match(0.99, stored) else null
        }
        return matchName(nameTokens(expected), nameTokens(actual), scope)
    }

    /**
     * Coincidencia de nombres tolerante en las dos direcciones.
     *
     * Busca un tramo en [seen] que arranque en el primer nombre guardado y termine en el último,
     * con los nombres del medio libres (la billetera puede mostrar un subconjunto, agregar
     * segundos nombres o usar iniciales). También acepta el orden invertido y el nombre de pila
     * abreviado. Los tokens vecinos no se exigen: solo se prefieren.
     */
    private fun matchName(
        wanted: List<String>,
        seen: List<String>,
        scope: MatchScope,
    ): Match? {
        if (wanted.isEmpty() || seen.isEmpty()) return null
        if (wanted.joinToString(" ") == seen.joinToString(" ")) return Match(1.0, wanted.joinToString(" "))

        // La lista tiene un solo token: no hay tramo que armar.
        if (wanted.size == 1) return loneToken(wanted.first(), seen)

        val first = wanted.first()
        val last = wanted.last()
        var best: Match? = null
        fun keep(candidate: Match) {
            val current = best
            if (current == null || candidate.confidence > current.confidence) best = candidate
        }

        for (i in seen.indices) {
            for (j in i + 1 until seen.size) {
                val span = seen.subList(i, j + 1).joinToString(" ")
                val wide = j - i + 1 > wanted.size + 1
                val conf = if (wide && ALLOW_WIDE_SPAN) 0.95 else 0.96
                if (seen[i] == first && seen[j] == last) {
                    keep(Match(conf, span))
                } else if (ALLOW_REVERSED_ORDER && seen[i] == last && seen[j] == first) {
                    keep(Match(conf, span))
                } else if (ALLOW_ABBREVIATED_FIRST_NAME && seen[i].length >= 3 &&
                    first.startsWith(seen[i]) && seen[j] == last
                ) {
                    keep(Match(conf, span))
                }
            }
        }
        best?.let { return it }

        // Red ancha: un solo token suelto. Solo al leer una pantalla completa; en un campo
        // haría disparar por coincidencia parcial de nombre de pila con otra persona.
        if (scope == MatchScope.PANTALLA) {
            return loneToken(first, seen) ?: loneToken(last, seen)
        }
        return null
    }

    /**
     * Un token suelto (nombre de pila o apellido). Se prefiere la aparición con contexto de
     * oración; si no hay ninguna, igual se acepta con un punto menos de confianza.
     */
    private fun loneToken(token: String, seen: List<String>): Match? {
        var fallback: Match? = null
        for (idx in seen.indices) {
            if (seen[idx] != token) continue
            val beforeOk = seen.getOrNull(idx - 1)?.let { it in SENTENCE_WORDS } ?: true
            val afterOk = seen.getOrNull(idx + 1)?.let { it in SENTENCE_WORDS } ?: true
            if (beforeOk && afterOk) return Match(0.96, token)
            if (fallback == null) fallback = Match(0.95, token)
        }
        return fallback
    }

    private fun tokenSequencePresent(expected: String, actual: String): Boolean {
        val e = expected.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank)
        val a = actual.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank)
        return e.isNotEmpty() && a.windowed(e.size).any { it == e }
    }

    private fun nameTokens(value: String): List<String> =
        value.split(Regex("[^\\p{L}]+")).filter(String::isNotBlank)

    // Solo se usa para PREFERIR una aparición con contexto de oración, no para exigirla.
    private val SENTENCE_WORDS = setOf(
        "desde", "en", "por", "para", "con", "a", "de", "del", "y", "que", "quien",
        "realizo", "intento", "hizo", "envio", "transfirio", "solicito", "quiere", "ahora",
        "figura", "aparece", "esta", "fue", "es", "segun", "como", "al", "celular",
    )
}
