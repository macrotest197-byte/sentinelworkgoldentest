package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.DetectionPipeline
import com.sentinel.recaudacion.detection.EventDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba de extremo a extremo (lógica) de la entrada de texto consentida:
 * texto recibido -> matcher -> detección identificable, con deduplicación.
 * No envía Telegram ni activa alarmas (sólo analiza).
 */
class DetectionPipelineTest {

    private val listaDePrueba = listOf(
        BlacklistPerson(
            id = "persona-prueba-1",
            primaryName = "Juan Perez",
            knownNameVariants = listOf("Juan Carlos Perez"),
            identifiers = listOf(PersonIdentifier(RuleType.ALIAS, "patodm.nx")),
        )
    )

    @Test
    fun textoConAliasDeListaNegra_detectaLaPersona() {
        val dedup = EventDeduplicator(now = { 0L })
        val r = DetectionPipeline.analyze("Transferir a patodm.nx ahora", listaDePrueba, dedup)
        assertTrue(r.hasMatch)
        assertEquals("Juan Perez", r.matches.first().primaryName)
        assertEquals(RuleType.ALIAS, r.matches.first().matchType)
        assertFalse(r.duplicate)
    }

    @Test
    fun textoConNombreDeListaNegra_detectaLaPersona() {
        val dedup = EventDeduplicator(now = { 0L })
        val r = DetectionPipeline.analyze("Transferencia a Juan Carlos Perez desde cuenta", listaDePrueba, dedup)
        assertTrue(r.hasMatch)
        assertEquals("Juan Perez", r.matches.first().primaryName)
    }

    @Test
    fun mismoTextoDosVeces_noDuplicaCoincidencia() {
        var t = 0L
        val dedup = EventDeduplicator(windowMs = 60_000L, now = { t })
        val primera = DetectionPipeline.analyze("Transferir a patodm.nx", listaDePrueba, dedup)
        assertTrue(primera.hasMatch)
        val segunda = DetectionPipeline.analyze("Transferir a patodm.nx", listaDePrueba, dedup)
        assertFalse(segunda.hasMatch)     // deduplicada
        assertTrue(segunda.duplicate)
    }

    @Test
    fun textoSinCoincidencia_noDetectaNada() {
        val dedup = EventDeduplicator(now = { 0L })
        val r = DetectionPipeline.analyze("Transferir a otra.persona.distinta", listaDePrueba, dedup)
        assertFalse(r.hasMatch)
        assertFalse(r.duplicate)
    }
}
