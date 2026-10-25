package com.sentinel.recaudacion

import com.sentinel.recaudacion.csv.BlacklistCsv
import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.TextNormalizer
import com.sentinel.recaudacion.ui.blacklist.BlacklistSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Valida la importación multiperfil (archivo `import_preview.csv` en la raíz del proyecto):
 * cantidad variable de identificadores por persona, resolución por cada identificador, ceros
 * iniciales, normalización de alias, ausencia de conflictos y preservación de `pato-1`/`patodm.nx`.
 * No escribe en la base; solo valida la lógica. Si el CSV no está, la prueba se omite.
 */
class ImportacionListaNegraTest {

    private fun csv(): File? = listOf("../import_preview.csv", "import_preview.csv")
        .map(::File).firstOrNull { it.exists() }

    /** Reconstruye las personas desde las reglas del CSV (igual que BlacklistRepository.activePersons). */
    private fun personas(): List<BlacklistPerson> {
        val reglas = BlacklistCsv.preview(csv()!!.readText(Charsets.UTF_8)).valid.map { it.rule }
        return reglas.groupBy { it.personId ?: "r-${it.id}" }.map { (pid, rs) ->
            val primary = rs.firstNotNullOfOrNull { it.primaryName?.takeIf(String::isNotBlank) }
                ?: rs.firstOrNull { it.type == RuleType.NOMBRE }?.value.orEmpty()
            val variants = (rs.flatMap { it.knownNameVariants } +
                rs.filter { it.type == RuleType.NOMBRE }.map { it.value }).filter { it.isNotBlank() && it != primary }.distinct()
            BlacklistPerson(
                id = pid, primaryName = primary, knownNameVariants = variants,
                identifiers = rs.filter { it.type != RuleType.NOMBRE }.map { PersonIdentifier(it.type, it.value) },
            )
        }
    }

    @Test fun importa25Personas76Identificadores_sinErrores() {
        val f = csv(); assumeTrue("import_preview.csv no encontrado", f != null)
        val p = BlacklistCsv.preview(f!!.readText(Charsets.UTF_8))
        assertEquals(0, p.errorCount)
        val reglas = p.valid.map { it.rule }
        assertEquals("25 personas", 25, reglas.mapNotNull { it.personId }.distinct().size)
        val ids = reglas.filter { it.type != RuleType.NOMBRE }
        assertEquals("76 identificadores (CBU+alias)", 76, ids.size)
    }

    @Test fun cantidadVariableDeIdentificadores_sinConflictos() {
        val f = csv(); assumeTrue(f != null)
        val reglas = BlacklistCsv.preview(f!!.readText(Charsets.UTF_8)).valid.map { it.rule }
        // conflicto = mismo (tipo, valor normalizado) en >1 persona
        val byKey = HashMap<Pair<RuleType, String>, MutableSet<String?>>()
        reglas.forEach { byKey.getOrPut(it.type to TextNormalizer.normalizeForType(it.type, it.value)) { mutableSetOf() }.add(it.personId) }
        assertTrue("sin conflictos", byKey.none { it.value.size > 1 })
        // variabilidad: alguna persona con >= 6 identificadores (Sol: 3 CBU + 3 alias + nombre)
        val porPersona = reglas.groupBy { it.personId }.mapValues { it.value.size }
        assertTrue("hay personas con >=6 filas", porPersona.values.any { it >= 6 })
    }

    @Test fun cbu_22digitos_cerosConservados() {
        val f = csv(); assumeTrue(f != null)
        val cbus = BlacklistCsv.preview(f!!.readText(Charsets.UTF_8)).valid.map { it.rule }
            .filter { it.type == RuleType.CBU }
        assertEquals(40, cbus.size)
        assertTrue("todos 22 dígitos", cbus.all { it.value.length == 22 })
        assertTrue("hay CBU con ceros iniciales", cbus.any { it.value.startsWith("0") })
    }

    @Test fun pato1_conservaPatodm_yResuelvePorCadaIdentificador() {
        val f = csv(); assumeTrue(f != null)
        val pato = personas().first { it.id == "pato-1" }
        assertEquals("Pato Encargado", pato.primaryName)
        // resuelve por cada identificador de pato-1 (2 CBU + 2 alias) → misma persona
        pato.identifiers.forEach { id ->
            val r = BlacklistPersonMatcher.resolve(id.value, personas())
            assertEquals("${id.type}:${id.value} debe resolver a pato-1", "pato-1", r?.personId)
        }
        // patodm.nx sigue vinculado a pato-1
        assertEquals("pato-1", BlacklistPersonMatcher.resolve("patodm.nx", personas())?.personId)
    }

    @Test fun aliasNormalizado_mayusculasResuelven() {
        val f = csv(); assumeTrue(f != null)
        // un alias en mayúsculas debe resolver igual (normalización sin destruir el original)
        val r = BlacklistPersonMatcher.resolve("PATODM.NX", personas())
        assertEquals("pato-1", r?.personId)
    }

    @Test fun resumenHome_conteosRealesDesdeLasReglasPersistidas() {
        val f = csv(); assumeTrue(f != null)
        val reglas = BlacklistCsv.preview(f!!.readText(Charsets.UTF_8)).valid.map { it.rule }
        val r = BlacklistSummary.from(reglas)
        // Inicio en frío: la UI debe terminar mostrando estos conteos reales, no un cero.
        assertEquals("total de reglas", 101, r.total)
        assertEquals("reglas activas", 101, r.activas)
        assertEquals("personas", 25, r.personas)
        assertEquals("identificadores (CBU + alias)", 76, r.identificadores)
    }

    @Test fun tresRoxy_separadas() {
        val f = csv(); assumeTrue(f != null)
        val ps = personas()
        val roxys = ps.filter { it.id.contains("roxy") }
        assertEquals("3 Roxy distintas", 3, roxys.size)
        assertEquals(3, roxys.map { it.primaryName }.distinct().size)
    }
}
