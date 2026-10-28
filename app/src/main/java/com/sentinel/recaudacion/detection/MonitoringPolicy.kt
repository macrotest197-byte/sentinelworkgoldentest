package com.sentinel.recaudacion.detection

/**
 * Política de monitoreo, en texto único y versionado. Es la fuente de la pantalla de
 * consentimiento y del documento `POLITICA_MONITOREO.md`.
 *
 * Cambiar el texto obliga a subir [VERSION]: un consentimiento viejo deja de valer y se
 * vuelve a pedir.
 */
object MonitoringPolicy {

    const val VERSION = 1

    data class Section(val title: String, val body: String)

    val SECTIONS: List<Section> = listOf(
        Section(
            "Qué es esto",
            "SENTINEL está instalado en este teléfono de trabajo. Detecta cuando el destinatario " +
                    "de una transferencia coincide con una cuenta de la lista de la operación, y avisa.",
        ),
        Section(
            "Qué lee",
            "Solo ocho aplicaciones de billetera, y solo mientras están en pantalla: el campo de " +
                    "destinatario y el texto visible de esa pantalla.",
        ),
        Section(
            "Qué NO lee",
            "Contraseñas, PIN y códigos. Chats, correo, contactos y navegación. Saldos e importes. " +
                    "No registra las teclas que escribís.",
        ),
        Section(
            "Qué se guarda en el teléfono",
            "El historial de coincidencias: qué se detectó, a quién corresponde, cuándo y en qué " +
                    "aplicación. No se guarda el texto completo de las pantallas.",
        ),
        Section(
            "Qué se envía y a dónde",
            "Un mensaje por cada coincidencia a un grupo de Telegram de la operación, con: " +
                    "dispositivo, operador, dato detectado, persona, tipo de dato y hora. No se envían " +
                    "capturas ni importes.",
        ),
        Section(
            "Quién lo ve",
            "Los administradores que están en ese grupo.",
        ),
        Section(
            "Cómo se revoca",
            "Ajustes → Accesibilidad → desactivar SENTINEL. Y Ajustes → Teclados → desactivar el " +
                    "teclado SENTINEL. Se puede hacer en cualquier momento, sin avisar.",
        ),
        Section(
            "Qué pasa si se revoca",
            "El teléfono deja de reportar. SENTINEL nunca bloquea una transferencia: solo avisa.",
        ),
        Section(
            "Titularidad de la cuenta",
            "Si la billetera de este teléfono es una cuenta personal y no de la operación, queda " +
                    "declarado en la pantalla de inicio.",
        ),
    )
}