package com.sentinel.recaudacion.data.settings

import android.content.Context
import com.sentinel.recaudacion.detection.MonitoringPolicy

/**
 * Registro local de la aceptación de la política. Guarda versión aceptada, fecha e id de
 * instalación, que es lo que permite probar *cuándo* y *en qué teléfono* se aceptó.
 *
 * No hay servidor: el registro vive en el dispositivo. Es suficiente para que la aceptación
 * sea un acto con fecha y no un supuesto.
 */
class ConsentStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sentinel_consent", Context.MODE_PRIVATE)

    fun isAccepted(): Boolean = prefs.getInt(KEY_VERSION, 0) >= MonitoringPolicy.VERSION

    val acceptedVersion: Int get() = prefs.getInt(KEY_VERSION, 0)
    val acceptedAt: Long get() = prefs.getLong(KEY_AT, 0L)
    val acceptedDeviceId: String get() = prefs.getString(KEY_DEVICE, null).orEmpty()

    fun accept(deviceId: String) {
        prefs.edit()
            .putInt(KEY_VERSION, MonitoringPolicy.VERSION)
            .putLong(KEY_AT, System.currentTimeMillis())
            .putString(KEY_DEVICE, deviceId)
            .apply()
    }

    fun revoke() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_VERSION = "accepted_version"
        private const val KEY_AT = "accepted_at"
        private const val KEY_DEVICE = "accepted_device_id"
    }
}