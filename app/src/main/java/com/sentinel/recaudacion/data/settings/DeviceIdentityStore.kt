package com.sentinel.recaudacion.data.settings

import android.content.Context
import com.sentinel.recaudacion.BuildConfig
import java.util.UUID

/** Estado de registro del dispositivo en el inventario administrado. */
enum class DeviceRegistrationStatus { PENDIENTE, REGISTRADO }

/**
 * Identidad estable por instalación. Expone:
 *  - [id]: identificador interno estable (UUID), persiste entre reinicios y renombrados.
 *  - [friendlyName]: nombre visible asignado por el administrador (p. ej. «Celular Pato»).
 *  - metadatos de inventario: estado de registro, fechas de alta/actualización y versión de SENTINEL.
 * No inventa ningún servidor: todo se persiste localmente en SharedPreferences.
 */
class DeviceIdentityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sentinel_device", Context.MODE_PRIVATE)

    val id: String
        get() = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_ID, it)
                .putLong(KEY_REGISTERED_AT, now)
                .putLong(KEY_UPDATED_AT, now)
                .apply()
        }

    val friendlyName: String
        get() = prefs.getString(KEY_NAME, null)?.takeIf(String::isNotBlank) ?: "Celular ${id.take(8)}"

    /** true si el administrador asignó un nombre visible (dispositivo dado de alta). */
    val status: DeviceRegistrationStatus
        get() = if (prefs.getString(KEY_NAME, null)?.isNotBlank() == true)
            DeviceRegistrationStatus.REGISTRADO else DeviceRegistrationStatus.PENDIENTE

    /** Fecha de alta (primer arranque con identidad). Garantiza lectura aun en instalaciones previas. */
    val registeredAt: Long
        get() = prefs.getLong(KEY_REGISTERED_AT, 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis().also { prefs.edit().putLong(KEY_REGISTERED_AT, it).apply() }

    /** Última actualización de nombre/metadatos. */
    val updatedAt: Long
        get() = prefs.getLong(KEY_UPDATED_AT, registeredAt)

    /** Versión de SENTINEL registrada en el inventario. */
    val appVersion: String
        get() = prefs.getString(KEY_APP_VERSION, null) ?: BuildConfig.VERSION_NAME

    /** Snapshot completo de la identidad para registros/informes. */
    fun snapshot(): DeviceIdentitySnapshot = DeviceIdentitySnapshot(
        id = id,
        friendlyName = friendlyName,
        status = status,
        registeredAt = registeredAt,
        updatedAt = updatedAt,
        appVersion = appVersion,
    )

    /** Sincroniza la versión de SENTINEL en cada arranque (no altera id ni fecha de alta). */
    fun syncAppVersion(version: String = BuildConfig.VERSION_NAME) {
        if (prefs.getString(KEY_APP_VERSION, null) != version) {
            prefs.edit()
                .putString(KEY_APP_VERSION, version)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    /** Renombra el dispositivo desde el flujo de administrador (no modifica el id interno). */
    fun setAdministratorName(name: String?) {
        val clean = name?.trim()?.take(40)?.takeIf(String::isNotBlank)
        prefs.edit().apply {
            if (clean == null) remove(KEY_NAME) else putString(KEY_NAME, clean)
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }.apply()
    }

    companion object {
        private const val KEY_ID = "installation_id"
        private const val KEY_NAME = "administrator_device_name"
        private const val KEY_REGISTERED_AT = "registered_at"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_APP_VERSION = "app_version"
    }
}

/** Identidad del dispositivo en un instante dado (solo lectura). */
data class DeviceIdentitySnapshot(
    val id: String,
    val friendlyName: String,
    val status: DeviceRegistrationStatus,
    val registeredAt: Long,
    val updatedAt: Long,
    val appVersion: String,
)
