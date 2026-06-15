package com.lyvox.vault.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = DarkBg,
    primaryContainer = AccentMuted,
    onPrimaryContainer = AccentLight,
    secondary = AccentLight,
    onSecondary = DarkBg,
    secondaryContainer = AccentSubtle,
    onSecondaryContainer = AccentLight,
    tertiary = Info,
    onTertiary = DarkBg,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Danger,
    onError = DarkBg,
    errorContainer = DangerMuted,
    onErrorContainer = Danger
)

private val LightColorScheme = lightColorScheme(
    primary = AccentDark,
    onPrimary = LightSurface,
    primaryContainer = AccentMuted,
    onPrimaryContainer = AccentDark,
    secondary = Accent,
    onSecondary = LightSurface,
    secondaryContainer = AccentSubtle,
    onSecondaryContainer = AccentDark,
    tertiary = Info,
    onTertiary = LightSurface,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = Danger,
    onError = LightSurface,
    errorContainer = DangerMuted,
    onErrorContainer = Danger
)

/**
 * lyvox vault theme — dark-first, editorial, cinematic.
 *
 * Always starts in dark mode. Light mode is available via settings.
 */
@Composable
fun LyvoxTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Set status bar to match background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LyvoxTypography,
        content = content
    )
}
