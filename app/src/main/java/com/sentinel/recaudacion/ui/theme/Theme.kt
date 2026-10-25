package com.sentinel.recaudacion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Teal = Color(0xFF0B3D4A)
private val TealLight = Color(0xFF14788C)
private val Amber = Color(0xFFF2A900)
private val Critical = Color(0xFFD23D3D)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE7ED),
    onPrimaryContainer = Teal,
    secondary = TealLight,
    tertiary = Amber,
    error = Critical,
    background = Color(0xFFF6F8F9),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3E3),
    onPrimary = Color(0xFF00363F),
    primaryContainer = TealLight,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9CDCE8),
    tertiary = Amber,
    error = Color(0xFFFF8A80),
    background = Color(0xFF0E1416),
    surface = Color(0xFF151C1E),
)

@Composable
fun SentinelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Teal.toArgb()
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = SentinelTypography,
        content = content,
    )
}
