package com.sentinel.recaudacion.detection

/**
 * Selección **pura** del texto útil de una pantalla. Recibe lo que el recorrido extrajo y
 * devuelve solo lo que vale la pena analizar, con topes para no reventar memoria ni el matcher.
 *
 * Excluye campos de contraseña, nodos no visibles, vacíos y repetidos consecutivos.
 */
object ScreenTextSelection {

    const val MAX_NODES = 400
    const val MAX_CHARS = 4000
    const val MAX_NODE_CHARS = 300

    data class RawNode(
        val text: String?,
        val contentDescription: String?,
        val isPassword: Boolean,
        val isVisible: Boolean,
    )

    fun select(raw: List<RawNode>): List<String> {
        val out = ArrayList<String>(minOf(raw.size, MAX_NODES))
        var total = 0
        for (node in raw) {
            if (node.isPassword || !node.isVisible) continue
            val value = node.text?.trim().takeUnless { it.isNullOrBlank() }
                ?: node.contentDescription?.trim().takeUnless { it.isNullOrBlank() }
                ?: continue
            val clipped = if (value.length > MAX_NODE_CHARS) value.take(MAX_NODE_CHARS) else value
            if (out.lastOrNull() == clipped) continue
            if (total + clipped.length + 1 > MAX_CHARS) break
            out.add(clipped)
            total += clipped.length + 1
            if (out.size >= MAX_NODES) break
        }
        return out
    }
}
