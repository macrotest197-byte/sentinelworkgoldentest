package com.sentinel.recaudacion.detection

import com.sentinel.recaudacion.data.model.RuleType
import java.security.MessageDigest

object ScreenScanPolicy {

    private const val MIN_LEN = 12
    private const val MAX_LEN = 4000
    private const val MIN_NAME_CONFIDENCE = 0.96
    private const val MIN_IDENTIFIER_CONFIDENCE = 0.99

    private val CONTEXT_WORDS = setOf(
        "alias", "cbu", "cvu", "destinatario", "destinataria", "transferir", "transferencia",
        "enviar", "envio", "titular", "cuenta", "confirmar", "importe", "monto", "pesos",
        "agenda", "contacto", "destino", "recibir",
    )

    fun shouldScan(packageName: String?, text: String?): Boolean {
        if (packageName == null || packageName !in AccessibilityScanPolicy.ALLOWED_PACKAGES) return false
        val t = text?.trim().orEmpty()
        if (t.length !in MIN_LEN..MAX_LEN) return false
        if (t.none { it.isLetter() }) return false
        return hasTransferContext(t)
    }

    fun hasTransferContext(text: String): Boolean {
        val tokens = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        return tokens.any { it in CONTEXT_WORDS }
    }

    fun shouldAlert(matchType: RuleType, confidence: Double): Boolean = when (matchType) {
        RuleType.NOMBRE -> confidence >= MIN_NAME_CONFIDENCE
        else -> confidence >= MIN_IDENTIFIER_CONFIDENCE
    }

    fun screenKey(text: String): String {
        val normalized = TextNormalizer.normalizeText(text)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
