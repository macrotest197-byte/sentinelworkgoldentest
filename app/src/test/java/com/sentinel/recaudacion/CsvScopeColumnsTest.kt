package com.sentinel.recaudacion

import com.sentinel.recaudacion.csv.BlacklistCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvScopeColumnsTest {

    private val header = "tipo,valor,descripcion,severidad,activo,persona_id,nombre_principal,variantes,escaneo,excepto_en_dispositivos"

    @Test
    fun `lee las dos columnas nuevas`() {
        val csv = header + "\n" +
                "CBU,0000003100010000000777,Gerente de prueba,Critica,si,gerente-prueba,Sol Gerente Prueba,,ambos,Celular Pato\n"
        val p = BlacklistCsv.preview(csv)
        assertEquals(0, p.errorCount)
        assertEquals("ambos", p.valid.single().rule.scanRoutes)
        assertEquals("Celular Pato", p.valid.single().rule.exemptDevices)
    }

    @Test
    fun `sin valor en las columnas queda nulo`() {
        val csv = header + "\n" +
                "CBU,0000003100010000000888,Persona normal de prueba,Critica,si,normal-prueba,NORMAL PRUEBA PERSONA,,ambos,\n"
        val p = BlacklistCsv.preview(csv)
        assertEquals("ambos", p.valid.single().rule.scanRoutes)
        assertNull(p.valid.single().rule.exemptDevices)
    }
}