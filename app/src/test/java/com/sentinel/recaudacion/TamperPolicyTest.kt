package com.sentinel.recaudacion

import com.sentinel.recaudacion.detection.Guard
import com.sentinel.recaudacion.detection.GuardState
import com.sentinel.recaudacion.detection.TamperPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperPolicyTest {

    @Test
    fun avisaCuandoSeApagaLaAccesibilidad() {
        assertEquals(listOf(Guard.ACCESIBILIDAD), TamperPolicy.lost(GuardState.ALL_ON, GuardState(false, true)))
    }

    @Test
    fun avisaCuandoSeApagaElTeclado() {
        assertEquals(listOf(Guard.TECLADO), TamperPolicy.lost(GuardState.ALL_ON, GuardState(true, false)))
    }

    @Test
    fun avisaUnaSolaVezPorLasDos() {
        assertEquals(2, TamperPolicy.lost(GuardState.ALL_ON, GuardState(false, false)).size)
    }

    @Test
    fun noRepiteSiYaEstabaApagada() {
        assertTrue(TamperPolicy.lost(GuardState(false, true), GuardState(false, true)).isEmpty())
    }

    @Test
    fun noAvisaSiSeReactiva() {
        assertTrue(TamperPolicy.lost(GuardState(false, true), GuardState.ALL_ON).isEmpty())
    }

    @Test
    fun reconoceElComponenteEnLaListaDelSistema() {
        val lista = "otro.servicio/com.X:com.sentinel.recaudacion.debug/com.sentinel.recaudacion.input.SentinelAccessibilityService"
        assertTrue(TamperPolicy.isListed(lista, "com.sentinel.recaudacion.debug/com.sentinel.recaudacion.input.SentinelAccessibilityService"))
        assertFalse(TamperPolicy.isListed(lista, "com.sentinel.recaudacion/com.sentinel.recaudacion.input.SentinelAccessibilityService"))
        assertFalse(TamperPolicy.isListed(null, "cualquiera"))
    }
}
