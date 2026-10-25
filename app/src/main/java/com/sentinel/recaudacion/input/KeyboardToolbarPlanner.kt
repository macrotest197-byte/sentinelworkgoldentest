package com.sentinel.recaudacion.input

/**
 * Plan de la zona inteligente de la barra del teclado (izquierda · centro · derecha).
 * [centerIsPaste] = true indica que el centro es el botón 📋 Pegar; en ese caso [center] = null
 * y no hay sugerencias. Cuando hay sugerencias, [center] es la más probable.
 */
data class ToolbarPlan(
    val left: String?,
    val center: String?,
    val right: String?,
    val centerIsPaste: Boolean,
) {
    val isEmpty: Boolean get() = left == null && center == null && right == null && !centerIsPaste
}

/**
 * Decide, de forma **pura y testeable**, qué muestra la zona inteligente de la barra, con la
 * prioridad pedida:
 *  1) Si hay texto pegable y el campo admite escritura → **Pegar** en el centro (sin sugerencias).
 *  2) Si no hay texto pegable y el campo permite sugerencias → hasta 3 sugerencias
 *     (centro = la más probable, luego izquierda y derecha).
 *  3) Campo sin sugerencias / sensible → sin sugerencias (Pegar sí, si hay portapapeles).
 * Si hay un aviso de detección activo, la zona queda limpia (el aviso se muestra aparte).
 * No lee el portapapeles: recibe solo si hay texto disponible ([hasClipText]).
 */
object KeyboardToolbarPlanner {
    fun plan(
        hasClipText: Boolean,
        suggestionsAllowed: Boolean,
        warningVisible: Boolean,
        suggestions: List<String>,
    ): ToolbarPlan {
        if (warningVisible) return ToolbarPlan(null, null, null, false)
        // Prioridad 1: portapapeles con contenido pegable → Pegar centrado, sin sugerencias.
        if (hasClipText) return ToolbarPlan(left = null, center = null, right = null, centerIsPaste = true)
        // Prioridad 2/3: sugerencias solo donde están permitidas.
        if (!suggestionsAllowed) return ToolbarPlan(null, null, null, false)
        val s = suggestions.take(3)
        return ToolbarPlan(
            left = s.getOrNull(1),      // segunda más probable
            center = s.getOrNull(0),    // la más probable al centro
            right = s.getOrNull(2),     // tercera
            centerIsPaste = false,
        )
    }
}
