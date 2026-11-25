package com.engmate.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.rememberReduceMotion

/** Circular progress with content in the middle. [progress] is 0..1; it animates to its value unless motion is reduced. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = rememberReduceMotion()
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), if (reduce) tween(0) else tween(600), label = "ring")
    Box(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (shown > 0f) drawArc(color, -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Linear progress with a label above it, e.g. "Đã học 3/17 bài". */
@Composable
fun LinearProgressWithLabel(progress: Float, label: String, modifier: Modifier = Modifier, trailing: String? = null,
                            color: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, gapSize = 0.dp, drawStopIndicator = {})
    }
}

/** Quiz progress split into one segment per question; [done] segments are filled. */
@Composable
fun SegmentedProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.height(8.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo(done.toFloat(), 0f..total.coerceAtLeast(1).toFloat()) },
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total.coerceAtLeast(1)) { i ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.small)
                .background(if (i < done) colors.primary else colors.surfaceContainerHigh))
        }
    }
}
