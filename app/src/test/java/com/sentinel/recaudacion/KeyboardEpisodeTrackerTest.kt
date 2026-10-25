package com.sentinel.recaudacion

import com.sentinel.recaudacion.detection.KeyboardEpisodeTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardEpisodeTrackerTest {

    @Test
    fun matchNuevo_disparaDeteccion() {
        val t = KeyboardEpisodeTracker()
        val o = t.onInspect("K", textEmpty = false)
        assertTrue(o.newDetection)
        assertFalse(o.clearWarning)
    }

    @Test
    fun mismoMatch_noDuplica() {
        val t = KeyboardEpisodeTracker()
        assertTrue(t.onInspect("K", false).newDetection)
        assertFalse(t.onInspect("K", false).newDetection) // mismo valor: sin duplicar
    }

    @Test
    fun trasBloqueo_campoVacio_rearmaYMantieneAviso() {
        val t = KeyboardEpisodeTracker()
        t.onInspect("K", false)                 // detección + (el IME limpia el campo)
        val o = t.onInspect(null, textEmpty = true) // campo vacío tras el bloqueo
        assertFalse(o.newDetection)
        assertFalse(o.clearWarning)              // el aviso se mantiene
        // nuevo intento del mismo valor -> vuelve a detectarse (rearmado)
        assertTrue(t.onInspect("K", false).newDetection)
    }

    @Test
    fun textoPermitidoNuevo_ocultaAviso() {
        val t = KeyboardEpisodeTracker()
        t.onInspect("K", false)                 // hubo aviso
        t.onInspect(null, true)                 // se limpió el campo
        val o = t.onInspect(null, textEmpty = false) // el usuario escribe algo permitido
        assertFalse(o.newDetection)
        assertTrue(o.clearWarning)              // se oculta el aviso
    }

    @Test
    fun sinAvisoPrevio_textoNoCoincidente_noHaceNada() {
        val t = KeyboardEpisodeTracker()
        val o = t.onInspect(null, textEmpty = false)
        assertFalse(o.newDetection)
        assertFalse(o.clearWarning)
    }

    @Test
    fun reset_vuelveAlEstadoInicial() {
        val t = KeyboardEpisodeTracker()
        t.onInspect("K", false)
        t.reset()
        assertTrue(t.onInspect("K", false).newDetection) // tras reset, K es nuevo otra vez
    }
}
