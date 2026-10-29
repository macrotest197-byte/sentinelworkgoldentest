package com.sentinel.recaudacion

import android.app.Application
import com.sentinel.recaudacion.data.db.AppDatabase
import com.sentinel.recaudacion.data.repo.BlacklistRepository
import com.sentinel.recaudacion.data.repo.IncidentRepository
import com.sentinel.recaudacion.csv.BlacklistProvisioning
import com.sentinel.recaudacion.data.settings.SecureSettingsStore
import com.sentinel.recaudacion.data.settings.DeviceIdentityStore
import com.sentinel.recaudacion.data.settings.DeviceProvisioning
import com.sentinel.recaudacion.telegram.TelegramProvisioning
import com.sentinel.recaudacion.telegram.TelegramRepository
import kotlinx.coroutines.runBlocking

/** Contenedor simple de dependencias de la aplicaciÃ³n. */
class SentinelApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val blacklistRepository: BlacklistRepository by lazy {
        BlacklistRepository(database.blacklistDao())
    }

    val incidentRepository: IncidentRepository by lazy {
        IncidentRepository(database.incidentDao())
    }

    /** Dedup de la entrada de texto consentida (evita verificaciones repetidas del mismo texto). */
    val textInputDeduplicator: com.sentinel.recaudacion.detection.EventDeduplicator by lazy {
        com.sentinel.recaudacion.detection.EventDeduplicator()
    }

    private val secureStore: SecureSettingsStore by lazy { SecureSettingsStore(this) }
    val deviceIdentity: DeviceIdentityStore by lazy { DeviceIdentityStore(this) }

    val telegramRepository: TelegramRepository by lazy {
        TelegramRepository(secureStore, database.outboxDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Mantiene la versiÃ³n de SENTINEL al dÃ­a en el inventario del dispositivo (sin alterar id ni alta).
        runCatching { deviceIdentity.syncAppVersion() }
        runCatching { com.sentinel.recaudacion.work.GuardScheduler.ensurePeriodic(this) }
        // Aprovisionamiento de Telegram desde archivo del administrador (fuera de la UI).
        // Se ejecuta en segundo plano; nunca registra el token.
        Thread {
            runCatching { TelegramProvisioning.importIfPresent(this, secureStore) }
        }.start()
        Thread {
            runCatching { DeviceProvisioning.importIfPresent(this, deviceIdentity) }
        }.start()
        // CSV de administraciÃ³n: validar antes de agregar/actualizar reglas existentes.
        // Nunca se borra la lista completa; el archivo solo se elimina tras importarse.
        Thread {
            runCatching {
                runBlocking { BlacklistProvisioning.importIfPresent(this@SentinelApp, blacklistRepository) }
            }
        }.start()
    }
}
