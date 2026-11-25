package com.engmate.app.feature.profile.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.progress.presentation.ProgressSections
import com.engmate.app.feature.progress.presentation.ProgressViewModel

/** Hồ sơ tab: who you are on top, then your progress, in one scrolling page without sub-tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHomeScreen(
    profile: ProfileViewModel,
    progress: ProgressViewModel,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onStudy: () -> Unit,
    onReviewDue: () -> Unit,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val p by profile.state.collectAsStateWithLifecycle()
    val s by progress.state.collectAsStateWithLifecycle()
    // Pull to refresh syncs with other devices (from main).
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
        .padding(horizontal = screenPadding).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Text("Hồ sơ", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TopBarAction(R.drawable.ic_settings, "Cài đặt", onSettings)
        }
        val user = p.profile
        InfoCard(onClick = onEdit) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Avatar(user?.photoUrl, 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(user?.displayName ?: "Chưa có hồ sơ", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Sửa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                user?.let { LevelChip(it.englishLevel) }
                user?.let { Text("Mục tiêu ${it.dailyGoalMinutes}'/ngày", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        SectionHeader("Tiến độ")
        ProgressSections(s, progress::setRange, onStudy, onReviewDue)
    }
    }
}
