package com.engmate.app.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.engmate.app.core.ui.theme.SemanticPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast for the success colors on the app's light and dark surfaces. */
class SemanticColorsTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val lightSurface = Color(0xFFF8F9FF)
    private val darkSurface = Color(0xFF111318)
    private val darkCard = Color(0xFF191C20)

    @Test fun darkSuccessTextIsReadable() {
        assertTrue(contrast(SemanticPalette.SuccessDark, darkSurface) >= 4.5)
        assertTrue(contrast(SemanticPalette.SuccessDark, darkCard) >= 4.5)
        assertTrue(contrast(SemanticPalette.OnSuccessContainerDark, SemanticPalette.SuccessContainerDark) >= 4.5)
    }

    @Test fun lightSuccessTextIsReadable() {
        // successText on the background and text inside the success container.
        assertTrue(contrast(SemanticPalette.OnSuccessContainerLight, lightSurface) >= 4.5)
        assertTrue(contrast(SemanticPalette.OnSuccessContainerLight, SemanticPalette.SuccessContainerLight) >= 4.5)
    }

    @Test fun lightSuccessIsEnoughForIconsOnly() {
        // The team's #1E8450 is about 4.46:1 on the background: fine for icons (3:1), not for body text.
        val ratio = contrast(SemanticPalette.SuccessLight, lightSurface)
        assertTrue(ratio >= 3.0)
        assertTrue(ratio < 4.5)
    }
}
