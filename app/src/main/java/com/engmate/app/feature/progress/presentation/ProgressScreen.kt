package com.engmate.app.feature.progress.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.core.ui.theme.success
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

private fun number(n: Number) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi")).format(n.toLong())
private fun dayLabel(day: DayOfWeek) = if (day == DayOfWeek.SUNDAY) "CN" else "T${day.value + 1}"

/** Standalone Tiến độ page; the Hồ sơ tab embeds [ProgressSections] instead. */
@Composable fun ProgressScreen(vm: ProgressViewModel, onStudy: () -> Unit = {}, onReviewDue: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = screenPadding, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        ProgressSections(s, vm::setRange, onStudy, onReviewDue)
    }
}

/** Statistics blocks: empty state before any activity, otherwise stat tiles, chart, streak dots and quiz accuracy. */
@Composable
fun ProgressSections(s: ProgressState, onRangeChange: (Int) -> Unit, onStudy: () -> Unit, onReviewDue: () -> Unit) {
    when {
        s.loading -> LoadingState(rows = 2)
        s.error -> ErrorState("Không tải được dữ liệu tiến độ. Hãy mở lại màn hình.")
        s.isEmpty -> EmptyState("Chưa có hoạt động nào", "Hoàn thành bài học đầu tiên để bắt đầu chuỗi ngày học.", icon = R.drawable.ic_flame,
            action = { PrimaryButton("Học ngay", onStudy) })
        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                StatCard("${s.streak} ngày", "Chuỗi ngày học", R.drawable.ic_flame, Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
                StatCard("${number(s.xp)} XP", "Tổng XP", R.drawable.ic_bolt, Modifier.weight(1f), tint = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                StatCard("${number(s.learned)} từ", "Đã thuộc", R.drawable.ic_check_circle, Modifier.weight(1f), tint = MaterialTheme.colorScheme.success)
                StatCard("${number(s.due)} từ", if (s.due > 0) "Cần ôn · bấm để ôn" else "Cần ôn", R.drawable.ic_layers, Modifier.weight(1f),
                    onClick = if (s.due > 0) onReviewDue else null)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProgressScreen(vm: ProgressViewModel, refreshing: Boolean = false, onRefresh: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF3F9FF)).verticalScroll(rememberScrollState()).statusBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Thống kê tiến độ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ink)
        if (s.loading) CircularProgressIndicator()
        else if (s.error) Text("Không tải được dữ liệu tiến độ. Vui lòng mở lại màn hình.")
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(Modifier.background(Color(0xFFEBF1F8), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(16.dp), tint = blue)
                    Spacer(Modifier.width(6.dp))
                    Text("7 ngày qua", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink)
                    Text("  ▾", fontSize = 11.sp, color = muted)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Text("Thời gian học", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AppSegmentedTabs(listOf("7 ngày", "30 ngày"), if (s.rangeDays == 30) 1 else 0, { onRangeChange(if (it == 1) 30 else 7) }, Modifier.width(180.dp))
            }
            InfoCard { MinutesChart(s.chartMinutes, s.goalMinutes) }
            InfoCard {
                Text("Chuỗi 7 ngày", style = MaterialTheme.typography.titleSmall)
                StreakDots(s.activeDays7)
            }
            if (s.accuracy != null) InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    ProgressRing(s.accuracy / 100f, size = 72.dp, color = MaterialTheme.colorScheme.tertiary) {
                        Text("${s.accuracy}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text("Độ chính xác bài tập", style = MaterialTheme.typography.titleMedium)
                        Text("Trên ${s.quizCount} bài đã làm", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Bars of minutes per day with a dashed goal line; the highest bar is labeled. */
@Composable
private fun MinutesChart(minutes: List<Long>, goal: Int?) {
    val colors = MaterialTheme.colorScheme
    val accent = EngMateTheme.brand.accent
    val top = maxOf(minutes.maxOrNull() ?: 0L, goal?.toLong() ?: 0L, 10L)
    val today = LocalDate.now()
    val total = minutes.sum()
    Column(Modifier.semantics { contentDescription = "Tổng ${total} phút trong ${minutes.size} ngày" + (goal?.let { ", mục tiêu $it phút mỗi ngày" } ?: "") },
        verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$total phút", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (goal != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 16.dp, height = 2.dp).background(accent))
                Spacer(Modifier.width(Spacing.xs))
                Text("Mục tiêu $goal'", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().height(140.dp)) {
            Column(Modifier.fillMaxHeight().padding(end = Spacing.xs), verticalArrangement = Arrangement.SpaceBetween) {
                Text("$top'", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val n = minutes.size.coerceAtLeast(1)
                val slot = size.width / n
                val bar = (slot * if (n > 10) 0.6f else 0.5f)
                drawLine(colors.outlineVariant, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                minutes.forEachIndexed { i, m ->
                    val h = (m.toFloat() / top) * size.height
                    val met = goal != null && m >= goal
                    drawRoundRect(if (met) colors.primary else colors.primary.copy(alpha = if (m > 0) 0.55f else 0.15f),
                        Offset(i * slot + (slot - bar) / 2, size.height - h.coerceAtLeast(3.dp.toPx())), Size(bar, h.coerceAtLeast(3.dp.toPx())),
                        CornerRadius(4.dp.toPx()))
                }
                if (goal != null) {
                    val y = size.height - goal / top.toFloat() * size.height
                    drawLine(accent, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp)) {
            minutes.indices.forEach { i ->
                val day = today.minusDays((minutes.size - 1 - i).toLong())
                val show = minutes.size <= 7 || i % 7 == (minutes.size - 1) % 7
                Text(if (show) (if (minutes.size <= 7) dayLabel(day.dayOfWeek) else "${day.dayOfMonth}/${day.monthValue}") else "",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    }
}

/** One dot per day: filled mango when the learner studied, outlined otherwise. */
@Composable
private fun StreakDots(active: List<Boolean>) {
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        active.forEachIndexed { i, on ->
            val day = today.minusDays((active.size - 1 - i).toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${dayLabel(day.dayOfWeek)}: " + if (on) "có học" else "chưa học" }) {
                Box(Modifier.size(28.dp).background(if (on) EngMateTheme.brand.accent else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    Alignment.Center) {
                    if (on) Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_flame), null, Modifier.size(16.dp), tint = EngMateTheme.brand.onAccent)
                }
                Text(dayLabel(day.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
