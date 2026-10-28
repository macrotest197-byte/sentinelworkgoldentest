package com.sentinel.recaudacion.detection

import java.security.MessageDigest

/**
 * Política **pura y testeable** del escaneo de pantalla. Decide si una pantalla merece
 * analizarse; no decide coincidencias (eso es del matcher).
 *
 * Fail-closed igual que el resto: solo apps de la allowlist, texto con un largo razonable,
 * con letras y con contexto de transferencia.
 */
object ScreenScanPolicy {

    private const val MIN_LEN = 12
    private const val MAX_LEN = 4000

    /** Palabras que indican pantalla de transferencia / carga de destinatario. */
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

    /** Huella estable del texto normalizado: mismo contenido no re-analiza. */
    fun screenKey(text: String): String {
        val normalized = TextNormalizer.normalizeText(text)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
