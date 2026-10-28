package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.TextNormalizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun alias_ignoraMayusculasYEspacios() {
        assertEquals(
            TextNormalizer.normalizeForType(RuleType.ALIAS, "  PatoDM.NX   "),
            TextNormalizer.normalizeForType(RuleType.ALIAS, "patodm.nx"),
        )
    }

    @Test
    fun nombre_quitaAcentosYColapsaEspacios() {
        assertEquals(
            "juan perez",
            TextNormalizer.normalizeForType(RuleType.NOMBRE, "Juán   Pérez"),
        )
    }

    @Test
    fun cbu_conservaSoloDigitos_sinAlterarElNumero() {
        val a = TextNormalizer.normalizeForType(RuleType.CBU, "0000003100010000000001")
        val b = TextNormalizer.normalizeForType(RuleType.CBU, "0000 0031 0001 0000 0000 01")
        assertEquals(a, b)
        assertEquals("0000003100010000000001", a)
    }

    @Test
    fun cvu_distintosNumeros_noCoinciden() {
        val a = TextNormalizer.normalizeForType(RuleType.CVU, "0000007900000000000001")
        val b = TextNormalizer.normalizeForType(RuleType.CVU, "0000007900000000000002")
        assert(a != b)
    }

    @Test
    fun documento_conPuntos_seNormalizaADigitos() {
        assertEquals(
            "30123456",
            TextNormalizer.normalizeForType(RuleType.DOCUMENTO, "30.123.456"),
        )
    }

    @Test
    fun nombreAceptaSegundoNombreInicialAcentosYEspacios() {
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, "Santiago Gutierrez", " SÁNTIAGO   Nicolás Gutiérrez "))
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, "Santiago N. Gutierrez", "Santiago Nicolas Gutierrez"))
        assertEquals(1.0, TextNormalizer.match(RuleType.NOMBRE, "Santiago Gutierrez", "Santiago Gutierrez")!!.confidence, 0.0)
        assertEquals(0.96, TextNormalizer.match(RuleType.NOMBRE, "Santiago Gutierrez", "Transferencia a Santiago Nicolas Gutierrez desde cuenta")!!.confidence, 0.0)
    }

    @Test
    fun nombreRechazaApellidoDistintoYAceptaExtraONombreAdelante() {
        // Apellido distinto: sigue rechazado (y en CAMPO no hay red de token único).
        assertFalse(TextNormalizer.matches(RuleType.NOMBRE, "Santiago Gutierrez", "Santiago Gonzalez"))
        // Extra nombre adelante: ahora se acepta (antes lo bloqueaba el guardián de vecinos).
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, "Santiago Gutierrez", "Nicolas Santiago Gutierrez"))
        // Apellido de más: ahora se acepta.
        assertTrue(TextNormalizer.matches(RuleType.NOMBRE, "Santiago Gutierrez", "Santiago Gutierrez Lopez"))
    }

    @Test
    fun identificadoresYAliasConservanCoincidenciaConservadora() {
        assertTrue(TextNormalizer.matches(RuleType.CBU, "0000003100010000000001", "CBU 0000-0031-0001-0000-0000-01"))
        assertFalse(TextNormalizer.matches(RuleType.CBU, "0000003100010000000001", "0000003100010000000002"))
        assertTrue(TextNormalizer.matches(RuleType.ALIAS, "patodm.nx", "Transferir a patodm.nx ahora"))
        assertFalse(TextNormalizer.matches(RuleType.ALIAS, "ana", "banana"))
        assertFalse(TextNormalizer.matches(RuleType.ALIAS, "ana", "Transferir a ana ahora"))
    }

    @Test
    fun personaUnificaNombreVarianteYIdentificadoresConUmbralAlto() {
        val person = BlacklistPerson(
            id = "persona-1",
            primaryName = "Santiago Gutierrez",
            knownNameVariants = listOf("Santiago Nicolas Gutierrez", "Santiago N. Gutierrez"),
            identifiers = listOf(PersonIdentifier(RuleType.ALIAS, "santi.gutierrez")),
        )
        val byName = BlacklistPersonMatcher.match("Transferencia a Santiago Nicolas Gutierrez desde cuenta", listOf(person))
        assertEquals(1, byName.size)
        assertEquals("Santiago Gutierrez", byName.single().primaryName)
        assertTrue(byName.single().confidence >= TextNormalizer.MIN_CONFIDENCE)

        assertEquals(1, BlacklistPersonMatcher.match("Destinatario santi.gutierrez", listOf(person)).size)
        // En CAMPO, un nombre de pila suelto no alcanza: "Santiago Gonzalez" es otra persona.
        assertTrue(BlacklistPersonMatcher.match("Santiago Gonzalez", listOf(person)).isEmpty())
        // Un apellido de más al final ya no bloquea.
        assertFalse(BlacklistPersonMatcher.match("Transferencia para Santiago Gutierrez Lopez", listOf(person)).isEmpty())
    }
}
