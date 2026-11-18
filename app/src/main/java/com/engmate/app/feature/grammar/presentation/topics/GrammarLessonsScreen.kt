package com.engmate.app.feature.grammar.presentation.topics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*

@Composable
fun GrammarLessonsScreen(viewModel: GrammarLessonsViewModel, onOpenLesson: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar("Ngữ pháp", onBack = null) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.failed -> EmptyState("Không tải được bài học", "Hãy mở lại ứng dụng và thử lại.")
                state.lessons.isEmpty() -> EmptyState("Chưa có bài ngữ pháp", "Bài học sẽ xuất hiện ở đây khi được thêm vào.")
                else -> {
                    if (state.levels.size > 1) ChipRow(listOf<String?>(null) + state.levels, state.level,
                        { it ?: "Tất cả" }, viewModel::filter, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.visible, key = { it.id }) { lesson ->
                            ListItem(
                                leadingContent = { LevelBadge(lesson.level) },
                                headlineContent = { Text(lesson.title) },
                                supportingContent = lesson.summary?.let { s ->
                                    { Text(s, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                },
                                trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
                                modifier = Modifier.clickable { onOpenLesson(lesson.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
