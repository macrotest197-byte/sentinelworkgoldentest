package com.sentinel.recaudacion.detection

import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.data.model.Severity

data class PersonMatch(
    val personId: String,
    val primaryName: String,
    val matchedValue: String,
    val confidence: Double,
    val matchType: RuleType,
    val severity: Severity,
)

/**
 * Resultado resuelto: el identificador que coincidió y la persona a la que corresponde.
 * Si el mismo identificador está asociado a más de una persona, [ambiguous] = true y la
 * persona queda sin resolver (no se elige una arbitrariamente).
 */
data class ResolvedMatch(
    val matchType: RuleType,
    val matchedValue: String,
    val confidence: Double,
    val severity: Severity,
    val personId: String?,
    val personName: String?,
    val ambiguous: Boolean,
)

/** Pure matcher for a single subject with linked names and identifiers. */
object BlacklistPersonMatcher {
    fun match(text: String, persons: List<BlacklistPerson>): List<PersonMatch> =
        persons.mapNotNull { person ->
            val candidates = buildList {
                person.primaryName.takeIf(String::isNotBlank)?.let { add(RuleType.NOMBRE to it) }
                person.knownNameVariants.filter(String::isNotBlank).forEach { add(RuleType.NOMBRE to it) }
                person.identifiers.filter { it.value.isNotBlank() }.forEach { add(it.type to it.value) }
            }
            candidates.mapNotNull { (type, value) ->
                TextNormalizer.match(type, value, text)
                    ?.takeIf { it.confidence >= TextNormalizer.MIN_CONFIDENCE }
                    ?.let {
                        PersonMatch(
                            person.id,
                            person.primaryName.ifBlank { person.knownNameVariants.firstOrNull().orEmpty().ifBlank { value } },
                            value,
                            it.confidence,
                            type,
                            person.severity,
                        )
                    }
            }.maxByOrNull { it.confidence }
        }.sortedByDescending { it.confidence }

    /**
     * Resuelve la mejor coincidencia a una persona. Detecta ambigüedad: si el mismo
     * identificador (mismo tipo y valor normalizado) corresponde a varias personas, no elige
     * una; devuelve [ResolvedMatch.ambiguous] = true con persona sin resolver.
     */
    fun resolve(text: String, persons: List<BlacklistPerson>): ResolvedMatch? {
        val matches = match(text, persons)
        val best = matches.maxByOrNull { it.confidence } ?: return null
        val bestNorm = TextNormalizer.normalizeForType(best.matchType, best.matchedValue)
        val distinctPersons = matches.filter {
            it.matchType == best.matchType &&
                TextNormalizer.normalizeForType(it.matchType, it.matchedValue) == bestNorm
        }.map { it.personId }.distinct()
        val ambiguous = distinctPersons.size > 1
        return ResolvedMatch(
            matchType = best.matchType,
            matchedValue = best.matchedValue,
            confidence = best.confidence,
            severity = best.severity,
            personId = if (ambiguous) null else best.personId,
            personName = if (ambiguous) null else best.primaryName,
            ambiguous = ambiguous,
        )
    }
}
