package com.sentinel.recaudacion.data.settings

import android.content.Context
import com.sentinel.recaudacion.detection.GuardState

class GuardStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sentinel_guard", Context.MODE_PRIVATE)

    val hasKnown: Boolean get() = prefs.contains(KEY_A11Y)

    fun load(): GuardState = GuardState(
        accessibility = prefs.getBoolean(KEY_A11Y, false),
        keyboard = prefs.getBoolean(KEY_KEYBOARD, false),
    )

    fun save(state: GuardState) {
        prefs.edit()
            .putBoolean(KEY_A11Y, state.accessibility)
            .putBoolean(KEY_KEYBOARD, state.keyboard)
            .apply()
    }

    private companion object {
        const val KEY_A11Y = "accessibility_enabled"
        const val KEY_KEYBOARD = "keyboard_enabled"
    }
}
