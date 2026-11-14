package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF1E6),
    onPrimaryContainer = PrimaryGreen,
    secondary = SketchBlack,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF1E6),
    onSecondaryContainer = SketchBlack,
    background = SketchWhite,
    onBackground = SketchBlack,
    surface = SketchWhite,
    onSurface = SketchBlack,
    surfaceVariant = Color(0xFFF7F7F7),
    onSurfaceVariant = Color(0xFF555555),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = SketchWhite,
    surfaceContainerHighest = Color(0xFFEAEAEA),
    outline = SketchDivider,
    outlineVariant = Color(0xFFE0E0E0)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = TransitSurfaceVariant,
    onPrimaryContainer = PrimaryGreen,
    secondary = Color.White,
    onSecondary = SketchBlack,
    secondaryContainer = TransitSurfaceVariant,
    onSecondaryContainer = Color(0xFFF0F4F8),
    background = TransitDarkBg,
    onBackground = Color(0xFFF0F4F8),
    surface = TransitSurface,
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = TransitSurfaceVariant,
    onSurfaceVariant = Color(0xFFA0B0C4),
    surfaceContainerLowest = TransitDarkBg,
    surfaceContainerLow = Color(0xFF1C2636),
    surfaceContainer = TransitSurfaceVariant,
    surfaceContainerHigh = TransitSurface,
    surfaceContainerHighest = Color(0xFF233044),
    outline = TransitCardBorder,
    outlineVariant = TransitCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
