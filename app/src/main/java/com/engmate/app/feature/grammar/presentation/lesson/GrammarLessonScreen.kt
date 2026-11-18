package com.engmate.app.feature.grammar.presentation.lesson

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.core.ui.components.*
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsViewModel

@Composable
fun GrammarLessonScreen(viewModel: GrammarLessonsViewModel, lessonId: Long, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lesson = state.lesson(lessonId)
    Scaffold(topBar = { BackTopBar("Ngữ pháp", onBack) }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            lesson == null -> Box(Modifier.padding(padding)) {
                EmptyState("Bài học không còn tồn tại", action = { OutlinedButton(onClick = onBack) { Text("Quay lại") } })
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp).widthIn(max = 680.dp)) {
                LevelBadge(lesson.level)
                Spacer(Modifier.height(12.dp))
                Text(lesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                lesson.summary?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Text(lesson.content, style = MaterialTheme.typography.bodyLarge)
                if (lesson.examples.isNotEmpty()) {
                    SectionHeader("Ví dụ", Modifier.padding(top = 12.dp))
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            lesson.examples.forEachIndexed { i, ex ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(ex.en, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(ex.vi, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
