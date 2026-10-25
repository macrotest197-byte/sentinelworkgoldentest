package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDetectionTest {

    @Test
    fun canoniza_mismoNumeroDistintosFormatos() {
        val a = TextNormalizer.normalizePhone("+54 9 11 1234-5678")
        val b = TextNormalizer.normalizePhone("1112345678")
        val c = TextNormalizer.normalizePhone("54 9 11 1234 5678")
        assertEquals("1112345678", a)
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun coincide_numeroCompletoConSeparadores() {
        assertTrue(TextNormalizer.matches(RuleType.TELEFONO, "+54 9 11 1234-5678", "mi numero es 11 1234 5678"))
    }

    @Test
    fun noCoincide_fragmentoDentroDeOtroNumero() {
        // El teléfono objetivo (10 díg) NO debe coincidir como fragmento de un CBU u otro número largo.
        assertNull(TextNormalizer.match(RuleType.TELEFONO, "1112345678", "0000001112345678999999"))
    }

    @Test
    fun noCoincide_numeroDistinto() {
        assertNull(TextNormalizer.match(RuleType.TELEFONO, "1112345678", "1199999999"))
    }

    @Test
    fun telefonoCorto_noValida() {
        assertNull(TextNormalizer.match(RuleType.TELEFONO, "12345", "12345"))
    }

    @Test
    fun resuelveTelefonoAPersona() {
        val persona = BlacklistPerson(
            id = "op-1", primaryName = "Operador Prueba",
            identifiers = listOf(PersonIdentifier(RuleType.TELEFONO, "+54 9 11 1234-5678")),
        )
        val r = BlacklistPersonMatcher.resolve("transferir a 1112345678", listOf(persona))!!
        assertEquals("Operador Prueba", r.personName)
        assertEquals(RuleType.TELEFONO, r.matchType)
    }

    @Test
    fun mapType_reconoceTelefono() {
        assertEquals(RuleType.TELEFONO, com.sentinel.recaudacion.csv.BlacklistCsv.mapType("Teléfono"))
        assertEquals(RuleType.TELEFONO, com.sentinel.recaudacion.csv.BlacklistCsv.mapType("telefono"))
        assertEquals(RuleType.TELEFONO, com.sentinel.recaudacion.csv.BlacklistCsv.mapType("Celular"))
    }

    @Test
    fun csv_importaYExportaTelefono() {
        val csv = """tipo,valor,descripcion,severidad,activo,persona_id,nombre_principal,variantes
            Teléfono,+54 9 11 1234-5678,,CRITICA,si,op-1,Operador Prueba,""".trimIndent()
        val p = com.sentinel.recaudacion.csv.BlacklistCsv.preview(csv)
        assertEquals(0, p.errorCount)
        assertEquals(RuleType.TELEFONO, p.valid[0].rule.type)
        val round = com.sentinel.recaudacion.csv.BlacklistCsv.preview(
            com.sentinel.recaudacion.csv.BlacklistCsv.export(p.valid.map { it.rule })
        )
        assertEquals(0, round.errorCount)
        assertEquals(RuleType.TELEFONO, round.valid[0].rule.type)
    }
}
