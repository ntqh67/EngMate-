package com.engmate.app.feature.grammar.presentation.topics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.grammar.domain.GrammarLesson

/** Học → Ngữ pháp: progress over all lessons, a level filter and one row per lesson with its status. */
@Composable
fun GrammarLessonsScreen(viewModel: GrammarLessonsViewModel, onOpenLesson: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val pad = screenPadding
    when {
        state.loading -> LoadingState(Modifier.padding(pad), rows = 5, hero = false)
        state.failed -> ErrorState("Không tải được bài học. Hãy mở lại ứng dụng.")
        state.lessons.isEmpty() -> EmptyState("Chưa có bài ngữ pháp", "Bài học sẽ xuất hiện ở đây khi được thêm vào.", icon = R.drawable.ic_assignment)
        else -> LazyColumn(contentPadding = PaddingValues(start = pad, end = pad, top = Spacing.l, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item(key = "progress") {
                Row(verticalAlignment = Alignment.Bottom) {
                    val total = state.lessons.size
                    LinearProgressWithLabel(if (total == 0) 0f else state.doneCount / total.toFloat(),
                        "Đã học ${state.doneCount}/$total bài", Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) viewModel.search("") }) {
                        Icon(painterResource(if (searchOpen) R.drawable.ic_close else R.drawable.ic_search), if (searchOpen) "Đóng tìm bài" else "Tìm bài")
                    }
                }
            }
            item(key = "search") {
                AnimatedVisibility(searchOpen) {
                    AppSearchField(state.query, viewModel::search, "Tìm chủ điểm ngữ pháp", autoFocus = true)
                }
            }
            item(key = "filter") { ChipRow(listOf<String?>(null) + state.levels, state.level, { it ?: "Tất cả" }, viewModel::filter) }
            val groups = state.visible.groupBy { it.level }
            if (groups.isEmpty()) item(key = "none") {
                EmptyState("Không có bài phù hợp", "Thử từ khoá khác hoặc bỏ bộ lọc trình độ.", icon = R.drawable.ic_search,
                    action = { SecondaryButton("Xoá bộ lọc", { viewModel.filter(null); viewModel.search("") }) })
            }
            groups.forEach { (level, lessons) ->
                item(key = "h$level") {
                    SectionHeader("Trình độ $level" + if (level == state.profileLevel) " · phù hợp với bạn" else "")
                }
                item(key = "g$level") {
                    SurfaceGroup {
                        lessons.forEachIndexed { i, lesson ->
                            if (i > 0) GroupDivider(72.dp)
                            LessonRow(lesson, state.bestPercent[lesson.id]) { onOpenLesson(lesson.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: GrammarLesson, best: Int?, onClick: () -> Unit) {
    ListRow(lesson.title, supporting = lesson.summary, onClick = onClick,
        leading = { LevelChip(lesson.level, large = true) },
        trailing = {
            when {
                best == null -> StatusTag(ItemStatus.NEW, "Mới")
                best >= 80 -> StatusTag(ItemStatus.DONE, "$best%")
                else -> StatusTag(ItemStatus.IN_PROGRESS, "$best%")
            }
        })
}
