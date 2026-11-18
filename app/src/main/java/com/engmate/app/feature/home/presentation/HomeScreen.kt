package com.engmate.app.feature.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.LevelBadge
import com.engmate.app.core.ui.components.SectionHeader
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.vocabulary.domain.Word
import java.time.LocalTime

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "Chào buổi sáng"
    in 11..12 -> "Chào buổi trưa"
    in 13..17 -> "Chào buổi chiều"
    else -> "Chào buổi tối"
}

@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenLessons: () -> Unit, onOpenLesson: (Long) -> Unit, onOpenWord: (Word) -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    HomeContent(state, search, viewModel::onQueryChange, onOpenLessons, onOpenLesson, onOpenWord)
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onOpenLessons: () -> Unit,
    onOpenLesson: (Long) -> Unit,
    onOpenWord: (Word) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("${greeting()}, ${state.profile?.displayName ?: "bạn"}", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item {
            OutlinedTextField(search.query, onQueryChange, Modifier.fillMaxWidth().testTag("home_search"),
                placeholder = { Text("Tìm từ tiếng Anh…") }, singleLine = true, shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = if (search.query.isEmpty()) null else {
                    { TextButton(onClick = { onQueryChange("") }) { Text("Xoá") } }
                })
        }
        if (search.query.isNotBlank()) {
            if (search.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("search_progress")) }
            search.notice?.let { notice ->
                item { Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(search.results, key = { it.id }) { word -> WordRow(word) { onOpenWord(word) } }
        } else {
            item { SectionHeader("Tiếp tục học", action = if (state.lessons.isNotEmpty()) { { TextButton(onClick = onOpenLessons) { Text("Tất cả bài") } } } else null) }
            item {
                val next = state.nextLesson
                when {
                    state.failed -> Text("Không tải được dữ liệu học. Hãy mở lại ứng dụng.", color = MaterialTheme.colorScheme.error)
                    next == null -> Text("Chưa có bài học nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> NextLessonCard(next) { onOpenLesson(next.id) }
                }
            }
        }
    }
}

@Composable
private fun WordRow(word: Word, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(word.word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                word.partOfSpeech?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(word.meaningVi ?: word.meaningEn, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NextLessonCard(lesson: GrammarLesson, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBadge(lesson.level)
                Text("Ngữ pháp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(lesson.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            lesson.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onClick, Modifier.padding(top = 4.dp)) { Text("Học bài này") }
        }
    }
}
