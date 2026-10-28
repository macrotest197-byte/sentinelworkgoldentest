package com.sentinel.recaudacion.input

import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.recaudacion.detection.ScreenTextSelection

/**
 * Recorrido del árbol de la ventana activa. Sin estado y con topes: profundidad y cantidad de
 * nodos visitados. No decide nada, solo extrae; el filtrado fino es de [ScreenTextSelection].
 */
object ScreenTextCollector {

    private const val MAX_DEPTH = 25
    private const val MAX_VISITED = ScreenTextSelection.MAX_NODES * 2

    fun collect(root: AccessibilityNodeInfo?): List<String> {
        val start = root ?: return emptyList()
        val raw = ArrayList<ScreenTextSelection.RawNode>(64)
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(start to 0)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_VISITED) {
            val (node, depth) = stack.removeLast()
            visited++
            runCatching {
                raw.add(
                    ScreenTextSelection.RawNode(
                        text = node.text?.toString(),
                        contentDescription = node.contentDescription?.toString(),
                        isPassword = node.isPassword,
                        isVisible = node.isVisibleToUser,
                    ),
                )
            }
            if (depth >= MAX_DEPTH) continue
            runCatching {
                for (i in node.childCount - 1 downTo 0) {
                    node.getChild(i)?.let { stack.addLast(it to depth + 1) }
                }
            }
        }
        return ScreenTextSelection.select(raw)
    }
}
