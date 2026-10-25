package com.sentinel.recaudacion

import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.AccessibilityScanPolicy
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.EventDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityScanPolicyTest {

    @Test
    fun soloInspeccionaAppsAutorizadas() {
        val autorizadas = listOf(
            "com.mercadopago.wallet", "ar.com.bancar.uala", "com.brubank", "ar.com.personalpay",
            "com.buepp", "com.applemoncash", "com.astropaycard.android", "com.dolarapp",
        )
        autorizadas.forEach {
            assertTrue("$it debería estar autorizada", AccessibilityScanPolicy.shouldInspect(it, false, "patodm.nx"))
        }
        assertEquals(8, AccessibilityScanPolicy.ALLOWED_PACKAGES.size)
        assertFalse(AccessibilityScanPolicy.shouldInspect("com.whatsapp", false, "patodm.nx"))
        assertFalse(AccessibilityScanPolicy.shouldInspect("com.android.chrome", false, "patodm.nx"))
        assertFalse(AccessibilityScanPolicy.shouldInspect(null, false, "patodm.nx"))
    }

    @Test
    fun excluyeCamposDeContrasena() {
        assertFalse(AccessibilityScanPolicy.shouldInspect("com.mercadopago.wallet", true, "patodm.nx"))
    }

    @Test
    fun ignoraTextoVacioOMuyCorto() {
        assertFalse(AccessibilityScanPolicy.shouldInspect("com.mercadopago.wallet", false, ""))
        assertFalse(AccessibilityScanPolicy.shouldInspect("com.mercadopago.wallet", false, "ab"))
    }

    @Test
    fun etiquetaDeAppConocida() {
        assertEquals("Mercado Pago", AccessibilityScanPolicy.appLabel("com.mercadopago.wallet"))
        assertEquals("Ualá", AccessibilityScanPolicy.appLabel("ar.com.bancar.uala"))
        assertEquals("Brubank", AccessibilityScanPolicy.appLabel("com.brubank"))
    }

    private val persona = BlacklistPerson(
        id = "pato-1", primaryName = "Pato Encargado",
        identifiers = listOf(
            PersonIdentifier(RuleType.ALIAS, "patodm.nx"),
            PersonIdentifier(RuleType.CBU, "0000007900204331218738"),
        ),
    )

    @Test
    fun reutilizaElMatcher_coincidencia() {
        // El servicio pasa el texto del campo al MISMO matcher; no hay un segundo motor.
        val r = BlacklistPersonMatcher.resolve("0000007900204331218738", listOf(persona))!!
        assertEquals("Pato Encargado", r.personName)
        assertEquals(RuleType.CBU, r.matchType)
    }

    @Test
    fun sinCoincidencia_noAlerta() {
        assertNull(BlacklistPersonMatcher.resolve("cuenta cualquiera 12345", listOf(persona)))
    }

    @Test
    fun ambiguo_noAtribuyeArbitrariamente() {
        val otra = BlacklistPerson(
            id = "otra-2", primaryName = "Otra",
            identifiers = listOf(PersonIdentifier(RuleType.ALIAS, "patodm.nx")),
        )
        val r = BlacklistPersonMatcher.resolve("patodm.nx", listOf(persona, otra))!!
        assertTrue(r.ambiguous)
        assertNull(r.personName)
    }

    @Test
    fun dedup_mismoDestinatarioNoRepiteEnLaPantalla() {
        // Simula la clave de dedup por destinatario que usa el servicio.
        val dedup = EventDeduplicator(now = { 0L })
        val key = "pato-1|CBU|0000007900204331218738"
        assertTrue(dedup.admit(key))
        assertFalse(dedup.admit(key))
    }
}
