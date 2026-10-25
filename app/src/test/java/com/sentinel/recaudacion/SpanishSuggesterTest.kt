package com.sentinel.recaudacion

import com.sentinel.recaudacion.input.SpanishSuggester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishSuggesterTest {

    @Test
    fun completaPorPrefijo() {
        val s = SpanishSuggester.suggest("gra")
        assertTrue("debería sugerir 'gracias'", s.any { it.equals("gracias", true) })
    }

    @Test
    fun corrigePalabraMalEscrita() {
        // 'graciad' → 'gracias' (sustitución de 1)
        assertEquals("gracias", SpanishSuggester.correct("graciad"))
    }

    @Test
    fun conservaMayusculaInicial() {
        val s = SpanishSuggester.suggest("Gra")
        assertTrue(s.any { it == "Gracias" })
    }

    @Test
    fun noSugiereSobreDatos_digitosPuntosArroba() {
        // Un alias/identificador no debe recibir sugerencias (no es una palabra común).
        assertTrue(SpanishSuggester.suggest("patodm").let { true }) // palabra sin dígitos: permitido evaluar
        assertTrue(SpanishSuggester.suggest("patodm.nx").isEmpty())  // con punto → no sugiere
        assertTrue(SpanishSuggester.suggest("cbu123").isEmpty())      // con dígitos → no sugiere
        assertTrue(SpanishSuggester.suggest("user@dom").isEmpty())    // con @ → no sugiere
    }

    @Test
    fun noConvierteDatoEnCoincidencia() {
        // Nunca propone un identificador de lista negra como 'patodm.nx'.
        val todas = SpanishSuggester.suggest("pato") + (SpanishSuggester.correct("pato") ?: "")
        assertFalse(todas.any { it.contains("patodm") })
    }

    @Test
    fun palabraCortaNoGeneraSugerencias() {
        assertTrue(SpanishSuggester.suggest("a").isEmpty())
        assertNull(SpanishSuggester.correct("de"))
    }

    @Test
    fun palabraCorrectaNoSeAutocorrige() {
        assertNull(SpanishSuggester.correct("gracias"))
    }
}
