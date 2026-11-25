package com.engmate.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.engmate.app.R
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.successContainer
import com.engmate.app.core.ui.theme.onSuccessContainer

/** The one highlighted block of a screen, on the brand color. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val brand = EngMateTheme.brand
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = brand.brand, contentColor = brand.onBrand, content = body)
    else Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = brand.brand, contentColor = brand.onBrand, content = body)
}

/** A plain white block; the default container for grouped content. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, contentPadding: PaddingValues = PaddingValues(Spacing.l),
             content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow, content = body)
    else Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, content = body)
}

/** Rows that belong together on one surface, separated by [GroupDivider] instead of each having a frame. */
@Composable
fun SurfaceGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(content = content)
    }
}

@Composable
fun GroupDivider(startIndent: androidx.compose.ui.unit.Dp = Spacing.l) {
    HorizontalDivider(Modifier.padding(start = startIndent), color = MaterialTheme.colorScheme.outlineVariant)
}

/** A number with its label, e.g. "6 ngày · Chuỗi ngày học". */
@Composable
fun StatCard(value: String, label: String, @DrawableRes icon: Int, modifier: Modifier = Modifier,
             tint: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null) {
    InfoCard(modifier, onClick) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.14f), CircleShape), Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = tint)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Progress of one lesson, topic or word. */
enum class ItemStatus { DONE, IN_PROGRESS, NEW }

/** Small status tag shown at the end of a [ListRow]; always text, never color alone. */
@Composable
fun StatusTag(status: ItemStatus, text: String) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        ItemStatus.DONE -> colors.successContainer to colors.onSuccessContainer
        ItemStatus.IN_PROGRESS -> colors.primaryContainer to colors.onPrimaryContainer
        ItemStatus.NEW -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    Row(Modifier.background(bg, MaterialTheme.shapes.small).padding(horizontal = Spacing.s, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (status == ItemStatus.DONE) { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = fg); Spacer(Modifier.width(2.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/**
 * One row in a list: optional leading visual, a headline that may wrap to [headlineLines], a one-line supporting text
 * and a trailing slot (status, chevron, action).
 */
@Composable
fun ListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    headlineLines: Int = 2,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(headline, style = MaterialTheme.typography.titleMedium, maxLines = headlineLines, overflow = TextOverflow.Ellipsis)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Chevron for rows that open another page. */
@Composable
fun RowChevron() = Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

/** Square shortcut with an icon and a label, used in 2×2 grids. */
@Composable
fun ShortcutTile(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                 tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 64.dp), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.14f), MaterialTheme.shapes.medium), Alignment.Center) {
                Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
            }
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
