package com.sentinel.recaudacion.telegram

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sentinel.recaudacion.SentinelApp
import java.util.concurrent.TimeUnit

/**
 * Procesa la cola de mensajes pendientes de Telegram. WorkManager garantiza reintentos
 * con backoff y espera a que haya red. La alarma local es independiente de este worker.
 */
class TelegramSendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as SentinelApp).telegramRepository
        return try {
            val remaining = repo.processPending()
            if (remaining > 0) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "telegram_outbox_flush"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TelegramSendWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
