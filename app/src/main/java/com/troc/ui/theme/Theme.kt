package com.troc.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkText,
    secondary = SecondaryCyan,
    onSecondary = Color.White,
    secondaryContainer = SecondaryCyan.copy(alpha = 0.2f),
    onSecondaryContainer = DarkText,
    tertiary = TertiaryEmerald,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryEmerald.copy(alpha = 0.2f),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    surfaceContainer = DarkSurface.copy(alpha = 0.8f),
    surfaceContainerHigh = DarkSurfaceVariant,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.2f),
    outline = DarkMuted.copy(alpha = 0.3f),
    outlineVariant = DarkMuted.copy(alpha = 0.15f),
    scrim = Color.Black.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = PrimaryPurple.copy(alpha = 0.12f),
    onPrimaryContainer = PrimaryPurple,
    secondary = SecondaryCyan,
    onSecondary = Color.White,
    secondaryContainer = SecondaryCyan.copy(alpha = 0.15f),
    onSecondaryContainer = SecondaryCyan,
    tertiary = TertiaryEmerald,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryEmerald.copy(alpha = 0.15f),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = LightMuted,
    surfaceContainer = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.15f),
    outline = LightMuted.copy(alpha = 0.3f),
    outlineVariant = LightMuted.copy(alpha = 0.15f),
    scrim = Color.Black.copy(alpha = 0.3f)
)

@Composable
fun TrocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeSetting: String = "system", // system, light, dark
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeSetting) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) DarkColorScheme else LightColorScheme
        }
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
