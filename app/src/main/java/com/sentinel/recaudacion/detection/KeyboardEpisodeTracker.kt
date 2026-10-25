package com.sentinel.recaudacion.detection

/**
 * Estado del episodio de detección del teclado. Decide, ante cada inspección del campo,
 * si hay una **detección nueva** (avisar + bloquear + registrar) o si hay que **ocultar el
 * aviso**. Evita avisos duplicados por el mismo valor y permite **rearmarse** para que un
 * nuevo intento (pegar/escribir otra vez el dato) vuelva a detectarse.
 *
 * Lógica (independiente de Android, testeable):
 *  - Match nuevo (clave distinta a la armada) → detección nueva (avisar/bloquear/registrar).
 *  - Mismo match → se ignora (sin duplicar).
 *  - Sin match y campo **vacío** (p. ej. justo después de limpiar el dato) → se rearma pero
 *    se mantiene el aviso (un nuevo pegado del mismo valor volverá a disparar).
 *  - Sin match con texto **nuevo no coincidente** → se oculta el aviso.
 */
class KeyboardEpisodeTracker {
    private var armedKey: String? = null
    private var warningActive = false

    data class Outcome(val newDetection: Boolean, val clearWarning: Boolean)

    fun onInspect(matchKey: String?, textEmpty: Boolean): Outcome {
        if (matchKey != null) {
            return if (matchKey != armedKey) {
                armedKey = matchKey
                warningActive = true
                Outcome(newDetection = true, clearWarning = false)
            } else {
                Outcome(newDetection = false, clearWarning = false)
            }
        }
        // sin coincidencia
        if (textEmpty) {
            armedKey = null // rearme; se conserva el aviso
            return Outcome(newDetection = false, clearWarning = false)
        }
        armedKey = null
        return if (warningActive) {
            warningActive = false
            Outcome(newDetection = false, clearWarning = true)
        } else {
            Outcome(newDetection = false, clearWarning = false)
        }
    }

    fun reset() {
        armedKey = null
        warningActive = false
    }
}
