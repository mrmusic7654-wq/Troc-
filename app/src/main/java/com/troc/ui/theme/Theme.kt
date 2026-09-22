package com.troc.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = DarkText,
    primaryContainer = DarkSurfaceVariant,
    secondary = SecondaryCyan,
    tertiary = TertiaryEmerald,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkMuted,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = LightSurface,
    primaryContainer = PrimaryPurple.copy(alpha = 0.1f),
    secondary = SecondaryCyan,
    tertiary = TertiaryEmerald,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightBackground,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightMuted,
    error = ErrorRed
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
