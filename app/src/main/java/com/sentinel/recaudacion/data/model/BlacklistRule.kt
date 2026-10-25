package com.sentinel.recaudacion.data.model

/** Modelo de dominio que usa la interfaz (con enums en lugar de strings). */
data class BlacklistRule(
    val id: Long = 0,
    val type: RuleType = RuleType.ALIAS,
    val value: String = "",
    val description: String? = null,
    val severity: Severity = Severity.CRITICA,
    val exactMatch: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val personId: String? = null,
    val primaryName: String? = null,
    val knownNameVariants: List<String> = emptyList(),
) {
    /** Valor parcialmente oculto para pantallas que puedan compartirse. */
    fun maskedValue(): String {
        val v = value.trim()
        if (v.length <= 4) return v
        val visible = 2
        val tailVisible = 2
        val head = v.take(visible)
        val tail = v.takeLast(tailVisible)
        return "$head${"•".repeat((v.length - visible - tailVisible).coerceAtLeast(1))}$tail"
    }
}
