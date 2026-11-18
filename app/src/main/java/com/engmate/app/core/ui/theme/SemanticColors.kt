package com.engmate.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colors Material 3 does not define. Wrong answers use the scheme's own error / errorContainer.
 * The dark set is picked from the background brightness, so it follows whatever scheme is active.
 */
internal object SemanticPalette {
    val SuccessLight = Color(0xFF1E8450)
    val SuccessContainerLight = Color(0xFFE6F6EC)
    val OnSuccessContainerLight = Color(0xFF14623B)

    // Lighter tones for dark backgrounds; text contrast stays above 4.5:1 (see SemanticColorsTest).
    val SuccessDark = Color(0xFF6DD58C)
    val SuccessContainerDark = Color(0xFF0B4F2C)
    val OnSuccessContainerDark = Color(0xFFB7F1C9)
}

val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

val ColorScheme.success: Color get() = if (isDark) SemanticPalette.SuccessDark else SemanticPalette.SuccessLight
val ColorScheme.successContainer: Color get() = if (isDark) SemanticPalette.SuccessContainerDark else SemanticPalette.SuccessContainerLight
val ColorScheme.onSuccessContainer: Color get() = if (isDark) SemanticPalette.OnSuccessContainerDark else SemanticPalette.OnSuccessContainerLight
