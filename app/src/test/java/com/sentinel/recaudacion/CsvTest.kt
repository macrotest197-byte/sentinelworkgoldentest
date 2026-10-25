package com.sentinel.recaudacion

import com.sentinel.recaudacion.csv.BlacklistCsv
import com.sentinel.recaudacion.csv.Csv
import com.sentinel.recaudacion.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {

    @Test
    fun parse_manejaComillasComasYSaltos() {
        val text = "a,b,c\n\"con, coma\",\"línea1\nlínea2\",simple"
        val rows = Csv.parse(text)
        assertEquals(2, rows.size)
        assertEquals(listOf("con, coma", "línea1\nlínea2", "simple"), rows[1])
    }

    @Test
    fun preview_cuentaNuevasDuplicadasYErrores() {
        val csv = """
            tipo,valor,descripcion,severidad,activo
            Alias,patodm.nx,Cuenta restringida,Crítica,si
            Alias,PATODM.NX,Duplicada por mayúsculas,Crítica,si
            CBU,0000003100010000000001,,Advertencia,no
            ,,sin tipo ni valor,,
            Raro,algo,tipo inválido,,
        """.trimIndent()
        val p = BlacklistCsv.preview(csv)
        // 3 válidas (patodm, cbu, y la duplicada cuenta como válida-duplicada)
        assertEquals(1, p.duplicateCount)       // PATODM.NX duplica a patodm.nx
        assertEquals(2, p.newCount)             // patodm.nx + CBU
        assertEquals(2, p.errorCount)           // fila vacía + tipo inválido
    }

    @Test
    fun preview_rechazaEncabezadoSinColumnasObligatorias() {
        val csv = "columna1,columna2\na,b"
        val p = BlacklistCsv.preview(csv)
        assertTrue(p.valid.isEmpty())
        assertEquals(1, p.errorCount)
    }

    @Test
    fun mapType_aceptaNombreYEtiqueta() {
        assertEquals(RuleType.CBU, BlacklistCsv.mapType("CBU"))
        assertEquals(RuleType.DOCUMENTO, BlacklistCsv.mapType("DNI/Documento"))
        assertEquals(RuleType.NOMBRE, BlacklistCsv.mapType("nombre"))
        assertEquals(null, BlacklistCsv.mapType("inexistente"))
    }

    @Test
    fun export_tieneEncabezadoYRespetaFormato() {
        val csv = BlacklistCsv.export(
            listOf(
                com.sentinel.recaudacion.data.model.BlacklistRule(
                    type = RuleType.ALIAS, value = "patodm.nx", description = "x",
                    severity = com.sentinel.recaudacion.data.model.Severity.CRITICA, active = true,
                )
            )
        )
        val rows = Csv.parse(csv)
        assertEquals(BlacklistCsv.HEADER, rows.first())
        assertEquals("ALIAS", rows[1][0])
        assertEquals("patodm.nx", rows[1][1])
    }

    @Test
    fun csv_preservaAsociacionDePersonaVariantesEIdentificadores() {
        val csv = """tipo,valor,descripcion,severidad,activo,persona_id,nombre_principal,variantes
            Nombre,Santiago Gutierrez,,CRITICA,si,persona-1,Santiago Gutierrez,"Santiago Nicolas Gutierrez|Santiago N. Gutierrez"
            Alias,santi.gutierrez,,CRITICA,si,persona-1,Santiago Gutierrez,"Santiago Nicolas Gutierrez|Santiago N. Gutierrez""".trimIndent()
        val preview = BlacklistCsv.preview(csv)
        assertEquals(0, preview.errorCount)
        assertEquals("persona-1", preview.valid[1].rule.personId)
        assertEquals("Santiago Gutierrez", preview.valid[1].rule.primaryName)
        assertEquals(listOf("Santiago Nicolas Gutierrez", "Santiago N. Gutierrez"), preview.valid[1].rule.knownNameVariants)
        val roundTrip = BlacklistCsv.export(preview.valid.map { it.rule })
        val parsed = BlacklistCsv.preview(roundTrip)
        assertEquals(0, parsed.errorCount)
        assertEquals("persona-1", parsed.valid[0].rule.personId)
    }
}
