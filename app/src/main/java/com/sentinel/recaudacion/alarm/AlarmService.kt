package com.sentinel.recaudacion.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Servicio en primer plano que reproduce la alarma local y publica la notificación
 * de alta prioridad con pantalla completa. Permite que la alarma siga sonando cuando
 * la app pasa a segundo plano, dentro de lo que Android permite.
 *
 * Límite real: en Android 12+ un servicio en primer plano no puede iniciarse mientras
 * la app está en segundo plano salvo excepciones. Por eso en la prueba manual el
 * servicio se arranca desde la interfaz (app en primer plano). Cuando exista el motor
 * de detección (Fase 5), el arranque se hará desde un contexto permitido.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm()
                return START_NOT_STICKY
            }
            else -> {
                val message = intent?.getStringExtra(EXTRA_MESSAGE)
                    ?: "Se detectó una coincidencia con una regla restringida."
                startForegroundWithAlarm(message)
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithAlarm(message: String) {
        val notification = AlarmNotifications.buildAlarmNotification(this, message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmNotifications.NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(AlarmNotifications.NOTIF_ID, notification)
        }
        // Evita alarmas simultáneas: start() devuelve false si ya había una activa.
        AlarmController.start(this)
        AlarmOverlay.show(this, message)
        // Corte automático: si nadie la reconoce, la alarma no queda sonando para siempre.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopAlarm() }, AUTO_STOP_MS)
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        AlarmOverlay.hide()
        AlarmController.stop(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        AlarmOverlay.hide()
        AlarmController.stop(this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.sentinel.recaudacion.alarm.ACTION_STOP"
        private const val AUTO_STOP_MS = 5_000L
        const val EXTRA_MESSAGE = "message"

        fun start(context: Context, message: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
