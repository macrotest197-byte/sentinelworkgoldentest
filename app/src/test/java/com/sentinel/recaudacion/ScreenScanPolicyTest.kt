package com.sentinel.recaudacion

import com.sentinel.recaudacion.detection.ScreenScanPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScanPolicyTest {

    private val mp = "com.mercadopago.wallet"
    private val pantalla = "Transferir Ingresa un alias CBU/CVU celular o nombre Santiago Gutierrez"

    @Test
    fun soloAppsDeLaAllowlist() {
        assertTrue(ScreenScanPolicy.shouldScan(mp, pantalla))
        assertFalse(ScreenScanPolicy.shouldScan("com.whatsapp", pantalla))
        assertFalse(ScreenScanPolicy.shouldScan(null, pantalla))
    }

    @Test
    fun exigeLargoRazonableYLetras() {
        assertFalse(ScreenScanPolicy.shouldScan(mp, "alias"))
        assertFalse(ScreenScanPolicy.shouldScan(mp, "1234567890123456"))
        assertTrue(ScreenScanPolicy.shouldScan(mp, pantalla))
    }

    @Test
    fun exigeContextoDeTransferencia() {
        assertFalse(ScreenScanPolicy.shouldScan(mp, "Santiago Gutierrez Ultimos movimientos"))
        assertTrue(ScreenScanPolicy.shouldScan(mp, "Confirmar transferencia a Myriam Soledad Zapata"))
        assertTrue(ScreenScanPolicy.hasTransferContext("Ingresa CBU o CVU"))
        assertFalse(ScreenScanPolicy.hasTransferContext("saldo disponible"))
    }

    @Test
    fun laHuellaIgnoraAcentosMayusculasYEspacios() {
        assertEquals(
            ScreenScanPolicy.screenKey("Transferir a Santiago Gutiérrez"),
            ScreenScanPolicy.screenKey("  TRANSFERIR   a santiago gutierrez "),
        )
        assertNotEquals(
            ScreenScanPolicy.screenKey("Transferir a Santiago Gutierrez"),
            ScreenScanPolicy.screenKey("Transferir a Myriam Zapata"),
        )
    }
}
