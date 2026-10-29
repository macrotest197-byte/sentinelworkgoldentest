package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.ScreenScanPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScanPolicyAlertTest {

    @Test
    fun nombreExigeConfianzaAlta() {
        assertFalse(ScreenScanPolicy.shouldAlert(RuleType.NOMBRE, 0.95))
        assertTrue(ScreenScanPolicy.shouldAlert(RuleType.NOMBRE, 0.96))
        assertTrue(ScreenScanPolicy.shouldAlert(RuleType.NOMBRE, 1.0))
    }

    @Test
    fun identificadorExigeCasiExacto() {
        assertFalse(ScreenScanPolicy.shouldAlert(RuleType.CBU, 0.96))
        assertTrue(ScreenScanPolicy.shouldAlert(RuleType.CBU, 0.99))
    }

    @Test
    fun aliasYDocumentoTambienSonIdentificadores() {
        assertTrue(ScreenScanPolicy.shouldAlert(RuleType.ALIAS, 0.99))
        assertFalse(ScreenScanPolicy.shouldAlert(RuleType.ALIAS, 0.95))
        assertTrue(ScreenScanPolicy.shouldAlert(RuleType.DOCUMENTO, 0.99))
    }
}
