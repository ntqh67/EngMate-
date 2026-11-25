package com.engmate.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engmate.app.R
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Nunito
import com.engmate.app.core.ui.theme.Spacing

/** Colors of a CEFR level, so lessons of different levels are told apart at a glance. */
@Composable
fun levelColors(level: String): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (level.uppercase()) {
        "A1" -> c.primaryContainer to c.onPrimaryContainer
        "A2" -> EngMateTheme.brand.brand to EngMateTheme.brand.onBrand
        "B1" -> c.tertiaryContainer to c.onTertiaryContainer
        "B2" -> c.secondaryContainer to c.onSecondaryContainer
        else -> c.tertiary to c.onTertiary
    }
}

/** CEFR level tag. [large] draws the square badge used at the start of lesson rows. */
@Composable
fun LevelChip(level: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val (bg, fg) = levelColors(level)
    if (large) Box(modifier.size(44.dp).background(bg, MaterialTheme.shapes.medium).semantics { contentDescription = "Trình độ $level" },
        contentAlignment = Alignment.Center) {
        Text(level, style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), color = fg)
    } else Text(level, modifier.background(bg, MaterialTheme.shapes.small).padding(horizontal = Spacing.s, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium, color = fg)
}

@Composable
private fun IconChip(@DrawableRes icon: Int, text: String, bg: Color, fg: Color, description: String, modifier: Modifier) {
    Row(modifier.heightIn(min = 32.dp).background(bg, CircleShape).padding(horizontal = Spacing.m, vertical = Spacing.xs)
        .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = fg)
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Chuỗi ngày học, on mango with dark text. */
@Composable
fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    val b = EngMateTheme.brand
    IconChip(R.drawable.ic_flame, "$days ngày", b.accent, b.onAccent, "Chuỗi ngày học: $days ngày", modifier)
}

/** XP on iris. [gain] shows "+10 XP" for a reward instead of a total. */
@Composable
fun XpChip(xp: Long, modifier: Modifier = Modifier, gain: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val text = (if (gain) "+" else "") + "%,d XP".format(xp).replace(',', '.')
    IconChip(R.drawable.ic_bolt, text, c.tertiaryContainer, c.onTertiaryContainer, if (gain) "Nhận $xp XP" else "Tổng $xp XP", modifier)
}

/** IPA transcription in a face that covers every IPA symbol. */
@Composable
fun IpaText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier, style = EngMateTheme.type.ipa, color = color)
}
