package com.sentinel.recaudacion.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controla la alarma local: sonido repetitivo + vibración intensa repetida.
 *
 * Es un singleton de proceso para garantizar **una sola alarma simultánea**:
 * si ya hay una activa, [start] no arranca otra (evita solapamientos).
 *
 * Limitaciones reales de Android (documentadas, no se prometen imposibles):
 *  - El volumen de la alarma usa el stream STREAM_ALARM, que en muchos dispositivos
 *    suena aunque el teléfono esté en vibración/silencio del timbre, pero NO se puede
 *    garantizar que ignore el modo "No molestar" (DND) sin que el usuario conceda la
 *    excepción correspondiente en los ajustes del sistema.
 *  - La vibración depende del hardware; algunos equipos limitan intensidad/duración.
 */
object AlarmController {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var previousAlarmVolume: Int? = null

    /** Arranca la alarma. Devuelve false si ya había una activa. */
    @Synchronized
    fun start(context: Context, raiseVolume: Boolean = true): Boolean {
        if (_active.value) return false
        val app = context.applicationContext
        startSound(app, raiseVolume)
        startVibration(app)
        _active.value = true
        return true
    }

    /** Reconoce/detiene la alarma: corta sonido y vibración y restaura el volumen. */
    @Synchronized
    fun stop(context: Context) {
        val app = context.applicationContext
        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: IllegalStateException) {
        } finally {
            player = null
        }
        vibrator?.cancel()
        vibrator = null
        restoreVolume(app)
        _active.value = false
    }

    private fun startSound(context: Context, raiseVolume: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (raiseVolume) {
            previousAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
        }
        var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            runCatching {
                setDataSource(context, uri)
                prepare()
                start()
            }.onFailure {
                // Si no hay tono disponible, al menos la vibración seguirá.
                release()
                player = null
            }
        }
    }

    private fun startVibration(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // Patrón intenso y repetido: espera, vibra fuerte, pausa corta...
        val timings = longArrayOf(0, 700, 300, 700, 300)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, 0) // 0 = repetir
        runCatching { vibrator?.vibrate(effect) }
    }

    private fun restoreVolume(context: Context) {
        val prev = previousAlarmVolume ?: return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, prev, 0) }
        previousAlarmVolume = null
    }
}
