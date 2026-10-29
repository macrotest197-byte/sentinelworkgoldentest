package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.detection.PersonScopeFilter
import com.sentinel.recaudacion.detection.ScanRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonScopeFilterTest {

    private fun persona(
        id: String = "p1",
        routes: Set<ScanRoute> = ScanRoute.entries.toSet(),
        exempt: List<String> = emptyList(),
    ) = BlacklistPerson(id = id, primaryName = "Nombre Apellido", scanRoutes = routes, exemptDevices = exempt)

    @Test
    fun `vacio significa ambas vias`() {
        assertEquals(ScanRoute.entries.toSet(), PersonScopeFilter.parseRoutes(null))
        assertEquals(ScanRoute.entries.toSet(), PersonScopeFilter.parseRoutes("  "))
    }

    @Test
    fun `ambos y todo son las dos vias`() {
        assertEquals(ScanRoute.entries.toSet(), PersonScopeFilter.parseRoutes("ambos"))
        assertEquals(ScanRoute.entries.toSet(), PersonScopeFilter.parseRoutes("todo"))
    }

    @Test
    fun `campo y pantalla se leen por separado`() {
        assertEquals(setOf(ScanRoute.CAMPO), PersonScopeFilter.parseRoutes("campo"))
        assertEquals(setOf(ScanRoute.PANTALLA), PersonScopeFilter.parseRoutes("pantalla"))
        assertEquals(ScanRoute.entries.toSet(), PersonScopeFilter.parseRoutes("campo|pantalla"))
    }

    @Test
    fun `ninguno desactiva el escaneo`() {
        assertTrue(PersonScopeFilter.parseRoutes("ninguno").isEmpty())
    }

    @Test
    fun `texto ilegible cae en ambas`() {
        assertEquals(ScanRoute.entries.toSet(), PersonScopeFilter.parseRoutes("cualquier cosa"))
    }

    @Test
    fun `dispositivos se separan por barra`() {
        assertEquals(listOf("Aquino Valeria", "Ortiz Patricia"), PersonScopeFilter.parseDevices("Aquino Valeria|Ortiz Patricia"))
        assertTrue(PersonScopeFilter.parseDevices(null).isEmpty())
    }

    @Test
    fun `excepcion ignora mayusculas y acentos`() {
        val p = persona(exempt = listOf("Gimenez Brian"))
        assertTrue(PersonScopeFilter.isExemptOn(p, "GIMENEZ BRIAN"))
        assertFalse(PersonScopeFilter.isExemptOn(p, "Celular Pato"))
        assertFalse(PersonScopeFilter.isExemptOn(p, null))
    }

    @Test
    fun `filtro saca exceptuados y respeta la via`() {
        val gerente = persona(id = "sol", exempt = listOf("Aquino Valeria"))
        val otro = persona(id = "otro")
        val soloPantalla = persona(id = "p", routes = setOf(ScanRoute.PANTALLA))

        val enAquino = PersonScopeFilter.filter(listOf(gerente, otro, soloPantalla), ScanRoute.CAMPO, "Aquino Valeria")
        assertEquals(listOf("otro"), enAquino.map { it.id })

        val enOtroEquipo = PersonScopeFilter.filter(listOf(gerente, otro), ScanRoute.CAMPO, "Celular Pato")
        assertEquals(listOf("sol", "otro"), enOtroEquipo.map { it.id })
    }
}