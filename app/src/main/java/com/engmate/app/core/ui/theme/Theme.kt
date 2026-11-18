package com.engmate.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Calm study palette: deep blue for structure, warm orange (secondary) reserved for XP and progress.
private val Light = lightColorScheme(
    primary = Color(0xFF1D5FA6), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF), onPrimaryContainer = Color(0xFF001C3B),
    secondary = Color(0xFF9A4600), onSecondary = Color.White,
    // Container stays a quiet blue: M3 uses it for tonal buttons and the nav indicator, which should not read as XP.
    secondaryContainer = Color(0xFFDAE3F3), onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF5B4FB0), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5DEFF), onTertiaryContainer = Color(0xFF170065),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA6C8FF), onPrimary = Color(0xFF003060),
    primaryContainer = Color(0xFF004787), onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFFFB68B), onSecondary = Color(0xFF532200),
    secondaryContainer = Color(0xFF3B4858), onSecondaryContainer = Color(0xFFDAE3F3),
    tertiary = Color(0xFFC8BFFF), onTertiary = Color(0xFF2C1A80),
    tertiaryContainer = Color(0xFF433697), onTertiaryContainer = Color(0xFFE5DEFF),
    background = Color(0xFF111318), onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318), onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable fun EngMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = AppShapes, content = content)
}
