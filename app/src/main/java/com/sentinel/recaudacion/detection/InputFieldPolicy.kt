package com.sentinel.recaudacion.detection

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Política **fail-closed** para el teclado SENTINEL: por defecto NO inspecciona nada.
 * Solo inspecciona un campo cuando la **app dueña** lo marca explícitamente con el
 * marcador privado de integración de SENTINEL en `privateImeOptions`, y aun así excluye
 * contraseñas, PIN, OTP, autocompletado y tipos desconocidos.
 *
 * Consecuencia (documentada, no se elude): las apps que NO agregan el marcador —p. ej.
 * Mercado Pago, Ualá, Brubank u otras billeteras de terceros— quedan FUERA de alcance.
 * Esto es intencional: evita que el teclado se convierta en un registrador general de
 * teclas sobre apps ajenas.
 */
object InputFieldPolicy {
    /** La app de trabajo autorizada marca su campo de destinatario con esto. */
    const val SAFE_FIELD_MARKER = "com.sentinel.recaudacion.SAFE_RECIPIENT_TEXT"
    /** Marcador del campo de demostración interno (pruebas dentro de SENTINEL). */
    const val SAFE_DEMO_MARKER = "com.sentinel.recaudacion.SAFE_DEMO_TEXT"

    fun canInspect(inputType: Int, imeOptions: Int, privateImeOptions: String?): Boolean {
        // 1) Fail-closed: sin marcador explícito, no se inspecciona.
        if (privateImeOptions?.split(',')?.any {
                it.trim() == SAFE_FIELD_MARKER || it.trim() == SAFE_DEMO_MARKER
            } != true
        ) return false
        // 2) Respeta la señal de "no aprendizaje personalizado".
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        // 3) Solo campos de texto normales.
        if (inputType == InputType.TYPE_NULL) return false
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        // 4) Excluye contraseñas y variantes sensibles.
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER
        ) return false
        // 5) Excluye autocompletado.
        val flags = inputType and InputType.TYPE_MASK_FLAGS
        if (flags and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0) return false
        return true
    }

    fun isDemoMarked(privateImeOptions: String?): Boolean =
        privateImeOptions?.split(',')?.any { it.trim() == SAFE_DEMO_MARKER } == true

    /**
     * ¿Se pueden ofrecer sugerencias de escritura en este campo? Es una decisión de teclado
     * (no de inspección): se excluyen contraseñas, PIN/OTP, campos numéricos/email/URI y los
     * que piden explícitamente no sugerir o no aprender. Nunca se ofrecen sugerencias con datos
     * sensibles, y las sugerencias jamás provienen de lo que el usuario escribió (no hay historial).
     */
    fun suggestionsAllowed(inputType: Int, imeOptions: Int): Boolean {
        if (inputType == InputType.TYPE_NULL) return false
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        // Solo texto normal (excluye number, phone, datetime → donde van PIN/OTP numéricos).
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER
        ) return false
        val flags = inputType and InputType.TYPE_MASK_FLAGS
        if (flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        return true
    }
}
