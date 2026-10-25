package com.sentinel.recaudacion

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.sentinel.recaudacion.detection.InputFieldPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la política fail-closed del teclado: sin marcador no inspecciona, y aun con
 * marcador excluye contraseñas, PIN/numéricos, autocompletado y señales de privacidad.
 */
class InputFieldPolicyTest {

    private val marker = InputFieldPolicy.SAFE_FIELD_MARKER
    private val textType = InputType.TYPE_CLASS_TEXT

    @Test
    fun sinMarcador_noInspecciona_failClosed() {
        assertFalse(InputFieldPolicy.canInspect(textType, 0, null))
        assertFalse(InputFieldPolicy.canInspect(textType, 0, "otro.marcador"))
    }

    @Test
    fun conMarcador_campoTextoNormal_siInspecciona() {
        assertTrue(InputFieldPolicy.canInspect(textType, 0, marker))
    }

    @Test
    fun password_seExcluyeAunqueEsteMarcado() {
        val pwd = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertFalse(InputFieldPolicy.canInspect(pwd, 0, marker))
        val visPwd = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertFalse(InputFieldPolicy.canInspect(visPwd, 0, marker))
    }

    @Test
    fun numerico_PINoOTP_seExcluye() {
        // Campos numéricos (clase NUMBER) no son texto -> fuera, aunque estén marcados.
        val numeric = InputType.TYPE_CLASS_NUMBER
        assertFalse(InputFieldPolicy.canInspect(numeric, 0, marker))
        val numericPwd = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertFalse(InputFieldPolicy.canInspect(numericPwd, 0, marker))
    }

    @Test
    fun autocompletado_seExcluye() {
        val auto = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
        assertFalse(InputFieldPolicy.canInspect(auto, 0, marker))
    }

    @Test
    fun sinAprendizajePersonalizado_seExcluye() {
        assertFalse(InputFieldPolicy.canInspect(textType, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, marker))
    }

    @Test
    fun tipoNulo_seExcluye() {
        assertFalse(InputFieldPolicy.canInspect(InputType.TYPE_NULL, 0, marker))
    }

    // --- Sugerencias (decisión de teclado, independiente de la inspección) ---

    @Test
    fun sugerencias_enCampoTextoNormal_permitidas() {
        assertTrue(InputFieldPolicy.suggestionsAllowed(textType, 0))
    }

    @Test
    fun sugerencias_enPassword_excluidas() {
        val pwd = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertFalse(InputFieldPolicy.suggestionsAllowed(pwd, 0))
    }

    @Test
    fun sugerencias_enNumerico_PINOTP_excluidas() {
        assertFalse(InputFieldPolicy.suggestionsAllowed(InputType.TYPE_CLASS_NUMBER, 0))
    }

    @Test
    fun sugerencias_enEmailYUri_excluidas() {
        val email = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertFalse(InputFieldPolicy.suggestionsAllowed(email, 0))
        val uri = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        assertFalse(InputFieldPolicy.suggestionsAllowed(uri, 0))
    }

    @Test
    fun sugerencias_conNoSuggestionsFlag_excluidas() {
        val noSug = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertFalse(InputFieldPolicy.suggestionsAllowed(noSug, 0))
    }
}
