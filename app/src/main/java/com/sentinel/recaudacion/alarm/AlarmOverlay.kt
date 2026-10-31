package com.sentinel.recaudacion.alarm

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Cartel de alerta como capa flotante sobre la app en foco.
 *
 * Android suprime el lanzamiento a pantalla completa cuando el teléfono está desbloqueado y en uso,
 * y ahí es justo donde está el operador. La capa flotante no depende de esa regla: aparece encima de
 * la billetera siempre, si el permiso de "aparecer sobre otras apps" está concedido.
 */
object AlarmOverlay {

    private var view: View? = null
    private var manager: WindowManager? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context, message: String) {
        if (!canShow(context)) return
        hide()
        val ctx = context.applicationContext
        val wm = ctx.getSystemService(WindowManager::class.java) ?: return

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B00020"))
            setPadding(56, 56, 56, 56)
        }
        root.addView(
            TextView(ctx).apply {
                text = "ALERTA DE SEGURIDAD"
                setTextColor(Color.WHITE)
                textSize = 26f
                gravity = Gravity.CENTER
            },
        )
        root.addView(
            TextView(ctx).apply {
                text = message
                setTextColor(Color.WHITE)
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 0)
            },
        )
        root.addView(
            Button(ctx).apply {
                text = "RECONOCER Y DETENER"
                textSize = 18f
                setOnClickListener { AlarmService.stop(ctx) }
                setPadding(0, 24, 0, 0)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 48 },
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )

        runCatching { wm.addView(root, params) }
        view = root
        manager = wm
    }

    fun hide() {
        val v = view
        val wm = manager
        if (v != null && wm != null) runCatching { wm.removeView(v) }
        view = null
        manager = null
    }
}