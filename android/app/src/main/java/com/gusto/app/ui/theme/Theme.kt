package com.gusto.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gusto.app.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    secondary = PrimaryAccentLight,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    outline = LightOutline,
    surfaceVariant = LightSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = LightBackground,
    secondary = PrimaryAccentLight,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkBackground,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    outline = DarkOutline,
    surfaceVariant = DarkSurfaceVariant
)

private val OledColorScheme = darkColorScheme(
    primary = LightBackground,
    secondary = PrimaryAccentLight,
    background = OledBackground,
    surface = OledSurface,
    onPrimary = OledBackground,
    onBackground = OledOnBackground,
    onSurface = OledOnSurface,
    outline = OledOutline,
    surfaceVariant = OledSurfaceVariant
)

@Composable
fun GustoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.OLED_BLACK -> OledColorScheme
        ThemeMode.SYSTEM -> if (systemInDark) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            val isDark = themeMode == ThemeMode.DARK || themeMode == ThemeMode.OLED_BLACK || (themeMode == ThemeMode.SYSTEM && systemInDark)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
