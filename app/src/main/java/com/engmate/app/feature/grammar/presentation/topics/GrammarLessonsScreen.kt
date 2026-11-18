package com.engmate.app.feature.grammar.presentation.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*

private val cardBlue = Color(0xFFD5E3FF)
private val tile = Color(0xFFE1F0FF)

@Composable
fun GrammarLessonsScreen(viewModel: GrammarLessonsViewModel, onOpenLesson: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Ngữ pháp", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.failed -> EmptyState("Không tải được bài học", "Hãy mở lại ứng dụng và thử lại.")
            state.lessons.isEmpty() -> EmptyState("Chưa có bài ngữ pháp", "Bài học sẽ xuất hiện ở đây khi được thêm vào.")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(state.query, viewModel::search, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Tìm chủ điểm ngữ pháp") }, shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFE6EEF7), focusedContainerColor = Color.White, unfocusedBorderColor = Color.Transparent))
                }
                item { ChipRow(listOf<String?>(null) + state.levels, state.level, { it ?: "Tất cả" }, viewModel::filter) }
                item {
                    Column(Modifier.fillMaxWidth().background(cardBlue, RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Text("${state.lessons.size} chủ điểm ngữ pháp", fontWeight = FontWeight.SemiBold)
                        Text("Trình độ ${state.levels.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val groups = state.visible.groupBy { it.level }
                if (groups.isEmpty()) item { EmptyState("Không tìm thấy chủ điểm phù hợp") }
                groups.forEach { (level, lessons) ->
                    item(key = "h$level") { Text("Trình độ $level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)) }
                    items(lessons, key = { it.id }) { lesson ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenLesson(lesson.id) }, shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFDDE4EB))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(44.dp).background(tile, RoundedCornerShape(12.dp)), Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_book), null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(lesson.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(lesson.summary, lesson.level).first(), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                LevelBadge(lesson.level)
                                Icon(painterResource(R.drawable.ic_chevron_right), null)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
