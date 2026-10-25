package com.sentinel.recaudacion.detection

import com.sentinel.recaudacion.data.model.RuleType
import java.text.Normalizer

/**
 * Normalización controlada para la comparación de la lista negra.
 *
 * Reglas:
 *  - Alias, nombre, código, cuenta, otro: minúsculas, se quitan acentos y se
 *    colapsan los espacios. Esto permite comparar sin diferencias de mayúsculas
 *    ni espacios, sin alterar el contenido significativo.
 *  - CBU, CVU, documento: se conservan únicamente los dígitos. NO se "corrigen"
 *    ni reordenan los números; sólo se eliminan separadores (espacios, puntos,
 *    guiones) para comparar un mismo número escrito con distinto formato.
 */
object TextNormalizer {

    const val MIN_CONFIDENCE = 0.95

    data class Match(val confidence: Double, val matchedText: String)

    fun normalizeText(input: String): String {
        val noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    fun normalizeNumeric(input: String): String =
        input.filter { it.isDigit() }

    /**
     * Canoniza un teléfono argentino para comparar formatos distintos del MISMO número:
     * deja solo dígitos y quita el código de país 54, el 0 de acceso nacional y el 9 de móvil.
     * Limitación conocida (prototipo): el prefijo 15 (que sustituye al 9) NO se unifica; el
     * administrador debe cargar los teléfonos en un formato consistente.
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

    /** High precision match for an observed text snippet. Names may contain extra
     * middle names/initials, while first and last names must match exactly. */
    fun matches(type: RuleType, stored: String, observed: String): Boolean {
        return match(type, stored, observed)?.confidence?.let { it >= MIN_CONFIDENCE } == true
    }

    /** Returns a conservative confidence for one rule found in the supplied text. */
    fun match(type: RuleType, stored: String, observed: String): Match? {
        val expected = normalizeForType(type, stored)
        val actual = normalizeForType(type, observed)
        if (expected.isEmpty() || actual.isEmpty()) return null
        if (type == RuleType.TELEFONO) {
            val target = normalizePhone(stored)
            if (target.length < 8) return null
            // El teléfono debe aparecer como un GRUPO de dígitos completo (con o sin separadores
            // comunes), no como fragmento dentro de otro número. Evita falsos positivos.
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
        val wanted = nameTokens(expected)
        val seen = nameTokens(actual)
        if (wanted.size < 2 || seen.size < wanted.size) return null
        // Find a contiguous name span inside surrounding sentence text. First and
        // last names must match exactly; only middle names/initials can vary.
        val wantedMiddle = wanted.drop(1).dropLast(1).filter { it.length > 1 }
        for (start in seen.indices.filter { seen[it] == wanted.first() }) {
            // An extra name BEFORE the first name (e.g. "Nicolas Santiago Gutierrez")
            // must be rejected, just like an extra surname after. Only sentence words
            // (or nothing) may precede the matched name span.
            val preceding = seen.getOrNull(start - 1)
            if (preceding != null && preceding !in SENTENCE_WORDS) continue
            val surnameAt = (start + 1 until seen.size).firstOrNull { seen[it] == wanted.last() } ?: continue
            val middle = seen.subList(start + 1, surnameAt)
            val following = seen.getOrNull(surnameAt + 1)
            if (following != null && following !in SENTENCE_WORDS) continue
            var at = 0
            var valid = true
            for (token in wantedMiddle) {
                val found = (at until middle.size).firstOrNull { i ->
                    middle[i] == token || (middle[i].length == 1 && token.startsWith(middle[i]))
                }
                if (found == null) { valid = false; break }
                at = found + 1
            }
            if (valid) return Match(0.96, seen.subList(start, surnameAt + 1).joinToString(" "))
        }
        return null
    }

    private fun tokenSequencePresent(expected: String, actual: String): Boolean {
        val e = expected.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank)
        val a = actual.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank)
        return e.isNotEmpty() && a.windowed(e.size).any { it == e }
    }

    private fun nameTokens(value: String): List<String> =
        value.split(Regex("[^\\p{L}]+")).filter(String::isNotBlank)

    // A following surname must not be mistaken for surrounding sentence text.
    // This conservative allowlist handles common Spanish sentence continuations.
    private val SENTENCE_WORDS = setOf(
        "desde", "en", "por", "para", "con", "a", "de", "del", "y", "que", "quien",
        "realizo", "intento", "hizo", "envio", "transfirio", "solicito", "quiere", "ahora",
        "figura", "aparece", "esta", "fue", "es", "segun", "como", "al", "celular",
    )
}
