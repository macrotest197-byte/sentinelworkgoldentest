package com.sentinel.recaudacion.data.model

/** Tipo de dato de la lista negra. El label se muestra en la interfaz en español. */
enum class RuleType(val label: String) {
    ALIAS("Alias"),
    CBU("CBU"),
    CVU("CVU"),
    NOMBRE("Nombre y apellido"),
    DOCUMENTO("DNI/Documento"),
    TELEFONO("Teléfono"),
    CODIGO("Código interno"),
    CUENTA("Número de cuenta"),
    OTRO("Otro");

    companion object {
        fun fromName(name: String?): RuleType =
            entries.firstOrNull { it.name == name } ?: OTRO
    }
}

/** Severidad de la regla. */
enum class Severity(val label: String) {
    ADVERTENCIA("Advertencia"),
    CRITICA("Crítica");

    companion object {
        fun fromName(name: String?): Severity =
            entries.firstOrNull { it.name == name } ?: ADVERTENCIA
    }
}
