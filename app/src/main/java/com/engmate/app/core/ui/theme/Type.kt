package com.engmate.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.engmate.app.R

// Nunito ships as one variable file; each weight picks its axis value.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(R.font.nunito_variable, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Headings and numbers. */
val Nunito = FontFamily(nunito(600), nunito(700), nunito(800))

/** Body, buttons and labels; designed for Vietnamese diacritics. */
val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    // Bold requests fall back to SemiBold rather than faux-bolding.
    Font(R.font.be_vietnam_pro_semibold, FontWeight.Bold),
)

private fun style(family: FontFamily, weight: FontWeight, size: Int, line: Int, tracking: Double) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (tracking / size).em)

// Figma "Font chữ": line heights already carry ~2sp extra over Material for stacked Vietnamese marks.
internal val AppTypography = Typography(
    displayLarge = style(Nunito, FontWeight.ExtraBold, 57, 64, -0.25),
    displayMedium = style(Nunito, FontWeight.ExtraBold, 45, 52, 0.0),
    displaySmall = style(Nunito, FontWeight.Bold, 36, 44, 0.0),
    headlineLarge = style(Nunito, FontWeight.Bold, 32, 40, 0.0),
    headlineMedium = style(Nunito, FontWeight.Bold, 28, 36, 0.0),
    headlineSmall = style(Nunito, FontWeight.Bold, 24, 32, 0.0),
    titleLarge = style(BeVietnamPro, FontWeight.SemiBold, 22, 30, 0.0),
    titleMedium = style(BeVietnamPro, FontWeight.SemiBold, 16, 24, 0.15),
    titleSmall = style(BeVietnamPro, FontWeight.SemiBold, 14, 20, 0.1),
    bodyLarge = style(BeVietnamPro, FontWeight.Normal, 16, 26, 0.15),
    bodyMedium = style(BeVietnamPro, FontWeight.Normal, 14, 22, 0.25),
    bodySmall = style(BeVietnamPro, FontWeight.Normal, 12, 18, 0.4),
    labelLarge = style(BeVietnamPro, FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = style(BeVietnamPro, FontWeight.SemiBold, 12, 16, 0.5),
    labelSmall = style(BeVietnamPro, FontWeight.Medium, 11, 16, 0.5),
)

/** Figma "Special" styles outside the Material scale. */
class ExtraTypography internal constructor() {
    /** Big numbers on stat tiles and result screens. */
    val stat = style(Nunito, FontWeight.ExtraBold, 40, 48, 0.0)
    /** A headword on word detail and flashcards. */
    val vocabulary = style(Nunito, FontWeight.ExtraBold, 32, 40, -0.25)
    /** IPA transcription; the system face covers every IPA symbol. */
    val ipa = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
}

internal val ExtraType = ExtraTypography()
