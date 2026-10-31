package com.sentinel.recaudacion.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sentinel.recaudacion.MainActivity
import com.sentinel.recaudacion.R

object AlarmNotifications {

    const val CHANNEL_ID = "sentinel_alarma"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas de seguridad",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alarma local cuando se detecta una coincidencia de la lista negra."
            enableVibration(false) // la vibración la maneja AlarmController (patrón repetido)
            setSound(null, null)   // el sonido lo maneja AlarmController (bucle)
            setBypassDnd(true)     // sólo surte efecto si el usuario concede acceso a No molestar
        }
        manager.createNotificationChannel(channel)
    }

    fun buildAlarmNotification(context: Context, message: String): Notification {
        ensureChannel(context)

        val fullScreenIntent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AlertActivity.EXTRA_MESSAGE, message)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val ackIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        val ackPi = PendingIntent.getService(
            context, 1, ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentPi = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ALERTA DE SEGURIDAD — RECAUDACIÓN")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPi)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(0, "RECONOCER Y DETENER", ackPi)
            .addAction(0, "VER ALERTA", fullScreenPi)
            .build()
    }
}
