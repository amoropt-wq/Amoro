package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.AccentTheme
import com.example.model.ThemeMode

@Composable
fun TeleCraftTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentTheme: AccentTheme = AccentTheme.TELEGRAM_CYAN,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val primary = Color(accentTheme.primaryHex)
    val secondary = Color(accentTheme.secondaryHex)

    val colorScheme = if (isDark) {
        val bg = if (themeMode == ThemeMode.AMOLED) AmoledBlack else DeepNavyBg
        val surf = if (themeMode == ThemeMode.AMOLED) AmoledSurface else SlateSurface
        val surfVar = if (themeMode == ThemeMode.AMOLED) Color(0xFF141923) else SlateSurfaceVariant
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.20f),
            onPrimaryContainer = secondary,
            secondary = secondary,
            onSecondary = Color(0xFF041421),
            secondaryContainer = secondary.copy(alpha = 0.16f),
            onSecondaryContainer = secondary,
            background = bg,
            onBackground = Color(0xFFF1F5F9),
            surface = surf,
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = surfVar,
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF334155),
            error = RoseError,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondary.copy(alpha = 0.14f),
            onSecondaryContainer = primary,
            background = LightBg,
            onBackground = Color(0xFF0F172A),
            surface = LightSurface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            error = Color(0xFFE11D48),
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
