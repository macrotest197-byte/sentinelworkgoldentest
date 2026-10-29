package com.sentinel.recaudacion.work

import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.data.settings.GuardStateStore
import com.sentinel.recaudacion.detection.GuardState
import com.sentinel.recaudacion.detection.TamperPolicy
import com.sentinel.recaudacion.telegram.TelegramSendWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Vigila que la accesibilidad y el teclado sigan encendidos. Avisa solo al apagarse. */
class GuardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SentinelApp ?: return Result.success()
        val resolver = applicationContext.contentResolver
        val pkg = applicationContext.packageName

        val accessibility = TamperPolicy.isListed(
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            "$pkg/com.sentinel.recaudacion.input.SentinelAccessibilityService",
        )
        val keyboard = TamperPolicy.isListed(
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS),
            "$pkg/com.sentinel.recaudacion.input.SentinelInputMethodService",
        )
        val current = GuardState(accessibility, keyboard)

        val store = GuardStateStore(applicationContext)
        val previous = if (store.hasKnown) store.load() else current
        val lost = TamperPolicy.lost(previous, current)
        store.save(current)
        if (lost.isEmpty()) return Result.success()

        val whenText = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val text = TamperPolicy.message(app.deviceIdentity.friendlyName, lost, whenText)
        val key = "tamper-${lost.joinToString("-")}-${System.currentTimeMillis() / 60_000}"
        runCatching { app.telegramRepository.enqueueAlert(text, key, app.deviceIdentity.id) }
        runCatching { TelegramSendWorker.enqueue(applicationContext) }
        return Result.success()
    }
}
