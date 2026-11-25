package com.engmate.app.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.engmate.app.R
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.onSuccessContainer
import com.engmate.app.core.ui.theme.rememberReduceMotion
import com.engmate.app.core.ui.theme.successContainer

/** Empty list or screen: a small illustration, what is missing, and the next step. */
@Composable
fun EmptyState(
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_sparkle,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.size(88.dp).background(EngMateTheme.brand.brand.copy(alpha = 0.55f), CircleShape), Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(40.dp), tint = EngMateTheme.brand.onBrand)
        }
        Spacer(Modifier.height(Spacing.s))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(Spacing.s)); action() }
    }
}

/** Something failed to load; says so plainly and offers a retry. */
@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Spacing.xxl).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onRetry != null) SecondaryButton("Thử lại", onRetry, icon = R.drawable.ic_refresh)
    }
}

/** Grey placeholder block that pulses while content loads. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val reduce = rememberReduceMotion()
    val alpha = if (reduce) 0.7f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
        a
    }
    Box(modifier.height(height).alpha(alpha).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
}

/** Skeleton of a typical screen: one hero block and a few rows. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, rows: Int = 4, hero: Boolean = true) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Đang tải" }, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (hero) SkeletonBlock(Modifier.fillMaxWidth(), height = 120.dp)
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                SkeletonBlock(Modifier.size(44.dp), height = 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f))
                    SkeletonBlock(Modifier.fillMaxWidth(0.45f), height = 12.dp)
                }
            }
        }
    }
}

enum class FeedbackKind { INFO, SUCCESS, WARNING, ERROR }

/** Message inside a page: always an icon plus text, so meaning never rests on color alone. */
@Composable
fun FeedbackBanner(
    text: String,
    modifier: Modifier = Modifier,
    kind: FeedbackKind = FeedbackKind.INFO,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val (bg: Color, fg: Color, icon: Int) = when (kind) {
        FeedbackKind.INFO -> Triple(c.primaryContainer, c.onPrimaryContainer, R.drawable.ic_info)
        FeedbackKind.SUCCESS -> Triple(c.successContainer, c.onSuccessContainer, R.drawable.ic_check_circle)
        FeedbackKind.WARNING -> Triple(c.secondaryContainer, c.onSecondaryContainer, R.drawable.ic_lightbulb)
        FeedbackKind.ERROR -> Triple(c.errorContainer, c.onErrorContainer, R.drawable.ic_warning)
    }
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium, color = bg, contentColor = fg) {
        Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                action?.invoke()
            }
        }
    }
}
