package com.sentinel.recaudacion.detection

/**
 * Política **pura y testeable** del servicio de accesibilidad (solo DETECCIÓN/aviso, nunca bloqueo).
 *
 * Fail-closed: solo se inspeccionan las apps de la lista permitida y nunca campos de contraseña.
 * No lee pantallas completas ni pulsaciones; el servicio solo pasa aquí el texto del campo que
 * cambió. Los paquetes son los reales verificados en el dispositivo.
 */
object AccessibilityScanPolicy {

    /** Billeteras autorizadas (8 paquetes reales verificados en el Moto G71). */
    val ALLOWED_PACKAGES: Set<String> = setOf(
        "com.mercadopago.wallet",   // Mercado Pago
        "ar.com.bancar.uala",       // Ualá
        "com.brubank",              // Brubank
        "ar.com.personalpay",       // Personal Pay
        "com.buepp",                // Buepp
        "com.applemoncash",         // Lemon
        "com.astropaycard.android", // AstroPay
        "com.dolarapp",             // ARQ (antes DolarApp)
    )

    private const val MIN_LEN = 4
    private const val MAX_LEN = 512

    /** Nombre legible de la app autorizada, o el paquete si no se conoce. */
    fun appLabel(pkg: String?): String = when (pkg) {
        "com.mercadopago.wallet" -> "Mercado Pago"
        "ar.com.bancar.uala" -> "Ualá"
        "com.brubank" -> "Brubank"
        "ar.com.personalpay" -> "Personal Pay"
        "com.buepp" -> "Buepp"
        "com.applemoncash" -> "Lemon"
        "com.astropaycard.android" -> "AstroPay"
        "com.dolarapp" -> "ARQ"
        else -> pkg.orEmpty()
    }

    /**
     * ¿Hay que inspeccionar este texto? Solo si: la app está autorizada, el campo NO es de
     * contraseña y el texto tiene un largo razonable. No decide coincidencia (eso es del matcher).
     */
    fun shouldInspect(packageName: String?, isPassword: Boolean, text: String?): Boolean {
        if (packageName == null || packageName !in ALLOWED_PACKAGES) return false
        if (isPassword) return false
        val t = text?.trim().orEmpty()
        return t.length in MIN_LEN..MAX_LEN
    }
}
