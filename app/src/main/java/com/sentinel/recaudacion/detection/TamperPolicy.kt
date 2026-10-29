package com.sentinel.recaudacion.detection

enum class Guard(val label: String, val how: String) {
    ACCESIBILIDAD("Accesibilidad", "Ajustes > Accesibilidad > SENTINEL"),
    TECLADO("Teclado", "Ajustes > Teclados > SENTINEL"),
}

data class GuardState(val accessibility: Boolean, val keyboard: Boolean) {
    companion object {
        val ALL_ON = GuardState(accessibility = true, keyboard = true)
    }
}

object TamperPolicy {

    fun lost(previous: GuardState, current: GuardState): List<Guard> = buildList {
        if (previous.accessibility && !current.accessibility) add(Guard.ACCESIBILIDAD)
        if (previous.keyboard && !current.keyboard) add(Guard.TECLADO)
    }

    fun isListed(enabled: String?, component: String): Boolean =
        enabled.orEmpty().split(':').any { it.trim() == component }

    fun message(device: String, lost: List<Guard>, whenText: String): String =
        "ALERTA SENTINEL - PROTECCION DESACTIVADA\n" +
            "Dispositivo: $device\n" +
            "Apagado: ${lost.joinToString(", ") { it.label }}\n" +
            "Como reactivar: ${lost.joinToString(" - ") { it.how }}\n" +
            "Fecha y hora: $whenText"
}
