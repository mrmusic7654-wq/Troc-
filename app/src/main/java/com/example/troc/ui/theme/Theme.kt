package com.example.troc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.troc.domain.model.AppSettings
import com.example.troc.domain.model.ThemeMode

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.25f),
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = CyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanSecondary.copy(alpha = 0.22f),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    error = RedError,
    onError = Color.White,
    errorContainer = RedError.copy(alpha = 0.2f),
    onErrorContainer = Color(0xFFFECACA),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0F172A)
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.18f),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = CyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanSecondary.copy(alpha = 0.16f),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    error = RedError,
    onError = Color.White,
    errorContainer = RedError.copy(alpha = 0.12f),
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9)
)

@Composable
fun TrocTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = Color(settings.accent.hex)
    val colorScheme = if (dark) darkScheme(accent) else lightScheme(accent)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = trocTypography(settings.fontScale),
        content = content
    )
}

@Composable
fun isTrocDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
