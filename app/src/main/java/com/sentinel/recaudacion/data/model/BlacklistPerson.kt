package com.sentinel.recaudacion.data.model

/** A person and the names/identifiers that should resolve to that same subject. */
data class BlacklistPerson(
    val id: String,
    val primaryName: String,
    val knownNameVariants: List<String> = emptyList(),
    val identifiers: List<PersonIdentifier> = emptyList(),
    val severity: Severity = Severity.CRITICA,
)

data class PersonIdentifier(
    val type: RuleType,
    val value: String,
)
