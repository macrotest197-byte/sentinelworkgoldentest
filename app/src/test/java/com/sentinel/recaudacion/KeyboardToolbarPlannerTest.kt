package com.sentinel.recaudacion

import com.sentinel.recaudacion.input.KeyboardToolbarPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardToolbarPlannerTest {

    private val sug = listOf("gracias", "grande", "grado")

    @Test
    fun portapapelesConTexto_muestraPegarCentrado_sinSugerencias() {
        val p = KeyboardToolbarPlanner.plan(
            hasClipText = true, suggestionsAllowed = true, warningVisible = false, suggestions = sug,
        )
        assertTrue(p.centerIsPaste)
        assertNull(p.left)
        assertNull(p.center)   // el centro lo ocupa Pegar (centerIsPaste), no una sugerencia
        assertNull(p.right)
    }

    @Test
    fun portapapelesVacio_ocultaPegar_muestraSugerencias_centroMasProbable() {
        val p = KeyboardToolbarPlanner.plan(
            hasClipText = false, suggestionsAllowed = true, warningVisible = false, suggestions = sug,
        )
        assertFalse(p.centerIsPaste)
        assertEquals("gracias", p.center)  // la más probable al centro
        assertEquals("grande", p.left)
        assertEquals("grado", p.right)
    }

    @Test
    fun campoSinSugerencias_noMuestraSugerencias() {
        val p = KeyboardToolbarPlanner.plan(
            hasClipText = false, suggestionsAllowed = false, warningVisible = false, suggestions = sug,
        )
        assertTrue(p.isEmpty)
    }

    @Test
    fun campoSensibleConPortapapeles_permitePegarPeroNoSugerencias() {
        // Campo sin sugerencias (p. ej. NO_SUGGESTIONS/contraseña) pero con texto pegable:
        // Pegar puede mostrarse; nunca sugerencias.
        val p = KeyboardToolbarPlanner.plan(
            hasClipText = true, suggestionsAllowed = false, warningVisible = false, suggestions = sug,
        )
        assertTrue(p.centerIsPaste)
        assertNull(p.left); assertNull(p.center); assertNull(p.right)
    }

    @Test
    fun avisoDeteccionActivo_barraLimpia() {
        val p = KeyboardToolbarPlanner.plan(
            hasClipText = true, suggestionsAllowed = true, warningVisible = true, suggestions = sug,
        )
        assertTrue(p.isEmpty)
        assertFalse(p.centerIsPaste)
    }

    @Test
    fun sinSugerenciasUtiles_barraNeutralSinInventar() {
        val p = KeyboardToolbarPlanner.plan(
            hasClipText = false, suggestionsAllowed = true, warningVisible = false, suggestions = emptyList(),
        )
        assertTrue(p.isEmpty)
    }

    @Test
    fun unaSolaSugerencia_vaAlCentro() {
        val p = KeyboardToolbarPlanner.plan(
            hasClipText = false, suggestionsAllowed = true, warningVisible = false, suggestions = listOf("hola"),
        )
        assertEquals("hola", p.center)
        assertNull(p.left); assertNull(p.right)
    }
}
