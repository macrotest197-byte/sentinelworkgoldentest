package com.sentinel.recaudacion.ui.input

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.sentinel.recaudacion.detection.InputFieldPolicy

/**
 * Campo de demostración interno, marcado con el marcador de demo de SENTINEL para que el
 * teclado lo inspeccione. Sirve para probar detección + prevención SIN alarma ni Telegram.
 * Escribí (o pegá) "sentinel.demo" con el teclado SENTINEL seleccionado para ver el bloqueo.
 */
class KeyboardPlaygroundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Campo de prueba del teclado SENTINEL (demo local, sin alarma ni Telegram).\n" +
                "1) Seleccioná el teclado SENTINEL.\n" +
                "2) Escribí o pegá: sentinel.demo\n" +
                "Deberías ver el aviso y el bloqueo del dato."
            setPadding(0, 0, 0, pad)
        })
        root.addView(EditText(this).apply {
            hint = "Campo de destinatario (demo)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            privateImeOptions = InputFieldPolicy.SAFE_DEMO_MARKER
            gravity = Gravity.TOP or Gravity.START
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        setContentView(root)
    }
}
