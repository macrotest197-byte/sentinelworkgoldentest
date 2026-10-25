package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de regresión del modelo de personas: un identificador detectado debe resolver a la
 * persona asociada. No envía Telegram ni activa alarmas (sólo lógica de resolución).
 */
class PersonResolutionTest {

    private val patoEncargado = BlacklistPerson(
        id = "pato-1",
        primaryName = "Pato Encargado",
        knownNameVariants = listOf("Patricio Encargado"),
        identifiers = listOf(
            PersonIdentifier(RuleType.ALIAS, "patodm.nx"),
            PersonIdentifier(RuleType.ALIAS, "pato.mp"),
            PersonIdentifier(RuleType.CBU, "0000003100010000000001"),
            PersonIdentifier(RuleType.CVU, "0000031000000000000099"),
            PersonIdentifier(RuleType.DOCUMENTO, "30123456"),
        ),
    )

    private val lista = listOf(patoEncargado)

    @Test
    fun aliasPrincipal_resuelveAPatoEncargado() {
        val r = BlacklistPersonMatcher.resolve("Transferir a patodm.nx ahora", lista)!!
        assertEquals("Pato Encargado", r.personName)
        assertEquals("pato-1", r.personId)
        assertEquals(RuleType.ALIAS, r.matchType)
        assertEquals("patodm.nx", r.matchedValue)
        assertFalse(r.ambiguous)
    }

    @Test
    fun segundoAlias_resuelveALaMismaPersona() {
        val r = BlacklistPersonMatcher.resolve("enviar a pato.mp", lista)!!
        assertEquals("Pato Encargado", r.personName)
        assertEquals("pato-1", r.personId)
    }

    @Test
    fun cbu_resuelveALaPersonaCorrecta() {
        val r = BlacklistPersonMatcher.resolve("CBU 0000003100010000000001", lista)!!
        assertEquals("Pato Encargado", r.personName)
        assertEquals(RuleType.CBU, r.matchType)
    }

    @Test
    fun cvu_resuelveALaPersonaCorrecta() {
        val r = BlacklistPersonMatcher.resolve("destino 0000031000000000000099", lista)!!
        assertEquals("Pato Encargado", r.personName)
        assertEquals(RuleType.CVU, r.matchType)
    }

    @Test
    fun varianteDeNombre_resuelveALaPersona() {
        val r = BlacklistPersonMatcher.resolve("Pago a Patricio Encargado", lista)!!
        assertEquals("Pato Encargado", r.personName)
        assertEquals(RuleType.NOMBRE, r.matchType)
    }

    @Test
    fun identificadorDesconocido_noInventaPersona() {
        val r = BlacklistPersonMatcher.resolve("Transferir a desconocido.xyz", lista)
        assertNull(r)
    }

    @Test
    fun mismoIdentificadorEnDosPersonas_registraAmbiguedad() {
        val otra = BlacklistPerson(
            id = "otra-2",
            primaryName = "Otra Persona",
            identifiers = listOf(PersonIdentifier(RuleType.ALIAS, "patodm.nx")),
        )
        val r = BlacklistPersonMatcher.resolve("a patodm.nx", listOf(patoEncargado, otra))!!
        assertTrue(r.ambiguous)
        assertNull(r.personId)
        assertNull(r.personName)
        assertEquals("patodm.nx", r.matchedValue)
    }
}
