package com.engmate.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// "Pastel Sky & Mango" (Figma brand page): pastel blue for structure, mango for streak and energy, iris for XP and rewards.
private val Light = lightColorScheme(
    primary = Color(0xFF2C71B7), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F0FF), onPrimaryContainer = Color(0xFF03294F),
    secondary = Color(0xFFA24E09), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1D1), onSecondaryContainer = Color(0xFF441C00),
    tertiary = Color(0xFF694CCD), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E5FE), onTertiaryContainer = Color(0xFF2A1560),
    background = Color(0xFFF3F9FF), onBackground = Color(0xFF101419),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF101419),
    surfaceVariant = Color(0xFFEBF1F8), onSurfaceVariant = Color(0xFF3E4349),
    outline = Color(0xFF757B81), outlineVariant = Color(0xFFDDE4EB),
    error = Color(0xFFBA2D3A), onError = Color.White,
    errorContainer = Color(0xFFFDE8E9), onErrorContainer = Color(0xFF7A1520),
    inverseSurface = Color(0xFF25292F), inverseOnSurface = Color(0xFFEBF1F8), inversePrimary = Color(0xFFA7CEFB),
    surfaceTint = Color(0xFF2C71B7),
    // Layers sit on the pale-blue background: white cards first, then cooler tints for nested or pressed areas.
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEBF1F8), surfaceContainerHigh = Color(0xFFE1EAF3), surfaceContainerHighest = Color(0xFFDDE4EB),
    surfaceBright = Color.White, surfaceDim = Color(0xFFDDE4EB),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA7CEFB), onPrimary = Color(0xFF03294F),
    primaryContainer = Color(0xFF0D4172), onPrimaryContainer = Color(0xFFE1F0FF),
    secondary = Color(0xFFFC9C62), onSecondary = Color(0xFF441C00),
    secondaryContainer = Color(0xFF612C04), onSecondaryContainer = Color(0xFFFFE1D1),
    tertiary = Color(0xFFCECAFF), onTertiary = Color(0xFF2A1560),
    tertiaryContainer = Color(0xFF3E2586), onTertiaryContainer = Color(0xFFE6E5FE),
    background = Color(0xFF101419), onBackground = Color(0xFFEBF1F8),
    surface = Color(0xFF101419), onSurface = Color(0xFFEBF1F8),
    surfaceVariant = Color(0xFF3E4349), onSurfaceVariant = Color(0xFFCBD2D9),
    outline = Color(0xFF9FA5AC), outlineVariant = Color(0xFF3E4349),
    error = Color(0xFFF2A0A7), onError = Color(0xFF5C0F17),
    errorContainer = Color(0xFF7A1520), onErrorContainer = Color(0xFFFDE8E9),
    inverseSurface = Color(0xFFEBF1F8), inverseOnSurface = Color(0xFF25292F), inversePrimary = Color(0xFF2C71B7),
    surfaceTint = Color(0xFFA7CEFB),
    surfaceContainerLowest = Color(0xFF0B0E12), surfaceContainerLow = Color(0xFF181C22),
    surfaceContainer = Color(0xFF1C2027), surfaceContainerHigh = Color(0xFF252A31), surfaceContainerHighest = Color(0xFF30353D),
    surfaceBright = Color(0xFF30353D), surfaceDim = Color(0xFF101419),
)

/** Brand colors Material has no role for. Text on [accent] is always [onAccent] (dark), never white. */
@Immutable
data class BrandColors(
    /** Highlight surfaces such as the daily-goal hero. */
    val brand: Color,
    val onBrand: Color,
    /** Mango: streak and energetic calls to action. */
    val accent: Color,
    val onAccent: Color,
)

private val LightBrand = BrandColors(brand = Color(0xFFA7CEFB), onBrand = Color(0xFF0D4172), accent = Color(0xFFF2843A), onAccent = Color(0xFF441C00))
private val DarkBrand = BrandColors(brand = Color(0xFF0D4172), onBrand = Color(0xFFE1F0FF), accent = Color(0xFFF2843A), onAccent = Color(0xFF441C00))

val LocalBrandColors = staticCompositionLocalOf { LightBrand }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** Access to EngMate tokens beside MaterialTheme: `EngMateTheme.brand`, `EngMateTheme.type`. */
object EngMateTheme {
    val brand: BrandColors @Composable get() = LocalBrandColors.current
    val type: ExtraTypography get() = ExtraType
}

@Composable fun EngMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
