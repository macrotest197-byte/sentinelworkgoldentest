package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.TextNormalizer
import com.sentinel.recaudacion.detection.TextNormalizer.MatchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dirección del match y reglas de pantalla. La lista guarda el nombre legal completo. */
class TextNormalizerScreenTest {

    private val legal = "Santiago Nicolas Gutierrez"

    @Test
    fun pantallaMasCortaQueLaLista_direccionQueFaltaba() {
        // El caso que hoy fallaba: la billetera muestra una versión parcial del documento.
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santiago Gutierrez", MatchScope.PANTALLA))
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santiago Gutierrez", MatchScope.CAMPO))
    }

    @Test
    fun pantallaConInicialesYNombreAbreviado() {
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santiago N. Gutierrez", MatchScope.PANTALLA))
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santi Gutierrez", MatchScope.PANTALLA))
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santi Gutierrez", MatchScope.CAMPO))
    }

    @Test
    fun ordenInvertidoComoMuestranVariosBancos() {
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "GUTIERREZ SANTIAGO", MatchScope.PANTALLA))
    }

    @Test
    fun nombreRodeadoDePalabrasQueNoEstanEnLaListaDeVecinos() {
        // Antes se descartaba por "nombre" adelante y "alias" atrás.
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "celular o nombre Santiago Gutierrez", MatchScope.PANTALLA))
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santiago Gutierrez Alias patocdm.mp", MatchScope.PANTALLA))
    }

    @Test
    fun tokenUnicoSoloEnPantalla() {
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santiago", MatchScope.PANTALLA))
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Gutierrez", MatchScope.PANTALLA))
        // En un campo, un solo token haría disparar por otra persona.
        assertFalse(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santiago Gonzalez", MatchScope.CAMPO))
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, legal, "Santiago Gonzalez", MatchScope.PANTALLA))
    }

    @Test
    fun laConfianzaBajaCuandoElTramoEsAnchoYNoCuandoEsExacto() {
        assertEquals(1.0, TextNormalizer.match(RuleType.NOMBRE, legal, legal, MatchScope.PANTALLA)!!.confidence, 0.0)
        assertEquals(
            0.96,
            TextNormalizer.match(RuleType.NOMBRE, legal, "Transferir a Santiago Gutierrez ahora", MatchScope.PANTALLA)!!.confidence,
            0.0,
        )
        val ancho = TextNormalizer.match(
            RuleType.NOMBRE, legal, "Santiago uno dos tres Gutierrez", MatchScope.PANTALLA,
        )
        assertTrue(ancho != null && ancho.confidence in 0.95..0.96)
    }

    @Test
    fun sigueRechazandoLoQueNoCorresponde() {
        assertNull(TextNormalizer.match(RuleType.NOMBRE, legal, "Myriam Zapata", MatchScope.PANTALLA))
        assertFalse(TextNormalizer.matches(RuleType.NOMBRE, legal, "Lucas Vila", MatchScope.PANTALLA))
    }
}
