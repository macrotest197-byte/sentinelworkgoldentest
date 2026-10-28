package com.sentinel.recaudacion

import com.sentinel.recaudacion.detection.ScreenTextSelection
import com.sentinel.recaudacion.detection.ScreenTextSelection.RawNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextSelectionTest {

    private fun node(text: String?, cd: String? = null, pwd: Boolean = false, visible: Boolean = true) =
        RawNode(text, cd, pwd, visible)

    @Test
    fun excluyeContrasenasEInvisibles() {
        val out = ScreenTextSelection.select(
            listOf(
                node("Santiago Gutierrez"),
                node("1234", pwd = true),
                node("Oculto", visible = false),
                node("Alias"),
            ),
        )
        assertEquals(listOf("Santiago Gutierrez", "Alias"), out)
    }

    @Test
    fun usaContentDescriptionSiNoHayTexto() {
        assertEquals(listOf("Destinatario"), ScreenTextSelection.select(listOf(node(null, "Destinatario"))))
    }

    @Test
    fun descartaVaciosYRepetidosConsecutivos() {
        val out = ScreenTextSelection.select(
            listOf(node("  "), node(null), node("Transferir"), node("Transferir"), node("CBU")),
        )
        assertEquals(listOf("Transferir", "CBU"), out)
    }

    @Test
    fun respetaElTopeDeCaracteres() {
        val muchos = (1..300).map { node("x".repeat(50)) }
        val out = ScreenTextSelection.select(muchos)
        val total = out.sumOf { it.length + 1 }
        assertTrue(total <= ScreenTextSelection.MAX_CHARS)
        assertTrue(out.size < muchos.size)
    }

    @Test
    fun respetaElTopeDeNodos() {
        val muchos = (1..(ScreenTextSelection.MAX_NODES + 50)).map { node("palabra$it") }
        assertTrue(ScreenTextSelection.select(muchos).size <= ScreenTextSelection.MAX_NODES)
    }
}
