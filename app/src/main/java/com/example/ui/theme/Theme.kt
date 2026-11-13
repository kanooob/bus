package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF1E6),
    onPrimaryContainer = PrimaryGreen,
    secondary = SketchBlack,
    onSecondary = Color.White,
    background = SketchWhite,
    onBackground = SketchBlack,
    surface = SketchWhite,
    onSurface = SketchBlack,
    surfaceVariant = Color(0xFFF7F7F7),
    onSurfaceVariant = SketchBlack,
    outline = SketchDivider
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = TransitSurfaceVariant,
    onPrimaryContainer = PrimaryGreen,
    secondary = Color.White,
    onSecondary = SketchBlack,
    background = TransitDarkBg,
    onBackground = Color(0xFFF0F4F8),
    surface = TransitSurface,
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = TransitSurfaceVariant,
    onSurfaceVariant = Color(0xFFA0B0C4),
    outline = TransitCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
