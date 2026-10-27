package com.lifeos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun LIFEOSTheme(
    themeMode: AppThemeMode? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ThemeManager.initialize(context)
    }

    val currentMode by ThemeManager.themeMode.collectAsState()
    val activeMode = themeMode ?: currentMode
    val isSystemInDark = isSystemInDarkTheme()

    val palette = when (activeMode) {
        AppThemeMode.SYSTEM -> if (isSystemInDark) DarkColorPalette else LightColorPalette
        AppThemeMode.DARK -> DarkColorPalette
        AppThemeMode.AMOLED -> AmoledColorPalette
        AppThemeMode.LIGHT -> LightColorPalette
    }

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.cyanAccent,
            tertiary = palette.success,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceElevated,
            error = palette.alert,
            onPrimary = Color.White,
            onSecondary = palette.background,
            onTertiary = palette.background,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.cyanAccent,
            tertiary = palette.success,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceElevated,
            error = palette.alert,
            onPrimary = Color.White,
            onSecondary = palette.background,
            onTertiary = palette.background,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onError = Color.White
        )
    }

    CompositionLocalProvider(
        LocalLifeOsColors provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}