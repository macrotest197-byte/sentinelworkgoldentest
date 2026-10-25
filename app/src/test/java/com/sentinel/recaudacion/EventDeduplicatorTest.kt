package com.sentinel.recaudacion

import com.sentinel.recaudacion.detection.EventDeduplicator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDeduplicatorTest {

    @Test
    fun primerEvento_seAdmite_yElDuplicadoInmediatoNo() {
        var t = 1000L
        val dedup = EventDeduplicator(windowMs = 10_000L, now = { t })
        assertTrue(dedup.admit("evt-1"))   // nuevo
        assertFalse(dedup.admit("evt-1"))  // duplicado dentro de la ventana
    }

    @Test
    fun clavesDistintas_seAdmitenAmbas() {
        var t = 0L
        val dedup = EventDeduplicator(windowMs = 10_000L, now = { t })
        assertTrue(dedup.admit("a"))
        assertTrue(dedup.admit("b"))
    }

    @Test
    fun reapareceDespuesDeLaVentana_seVuelveAAdmitir() {
        var t = 0L
        val dedup = EventDeduplicator(windowMs = 10_000L, now = { t })
        assertTrue(dedup.admit("evt"))
        t = 5_000L
        assertFalse(dedup.admit("evt")) // sigue dentro de la ventana
        t = 20_000L
        assertTrue(dedup.admit("evt"))  // fuera de la ventana -> nuevo evento
    }
}
