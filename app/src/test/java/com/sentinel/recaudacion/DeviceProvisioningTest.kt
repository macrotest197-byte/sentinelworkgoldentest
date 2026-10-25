package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.settings.DeviceProvisioning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProvisioningTest {
    @Test
    fun parseaNombreAdministradorSinTelegram() {
        assertEquals("Recaudación 01", DeviceProvisioning.parse("""{"deviceName":" Recaudación 01 "}"""))
    }

    @Test
    fun rechazaNombreAusenteOVacio() {
        assertNull(DeviceProvisioning.parse("{}"))
        assertNull(DeviceProvisioning.parse("""{"deviceName":"  "}"""))
        assertNull(DeviceProvisioning.parse("no json"))
    }
}
