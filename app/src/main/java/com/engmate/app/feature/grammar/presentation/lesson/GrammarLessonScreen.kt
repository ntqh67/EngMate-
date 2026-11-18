package com.engmate.app.feature.grammar.presentation.lesson

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsViewModel

private val blueCard = Color(0xFFD5E3FF)
private val chipBg = Color(0xFFE1F0FF)

@Composable
/** [onPractice] starts the quiz for this lesson; null keeps the quiz button disabled. */
fun GrammarLessonScreen(viewModel: GrammarLessonsViewModel, lessonId: Long, onBack: () -> Unit, onPractice: ((Long) -> Unit)? = null,
    onPronunciationScored: (Int) -> Unit = {}) {
    var practiceTarget by remember { mutableStateOf<String?>(null) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lesson = state.lesson(lessonId)
    var tab by rememberSaveable(lessonId) { mutableIntStateOf(0) }
    val pronounce = rememberPronouncer()
    practiceTarget?.let { com.engmate.app.feature.pronunciation.presentation.PronunciationDialog(it, { practiceTarget = null }, onPronunciationScored) }
    Scaffold(topBar = { BackTopBar(lesson?.title ?: "Ngữ pháp", onBack) }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            lesson == null -> Box(Modifier.padding(padding)) {
                EmptyState("Bài học không còn tồn tại", action = { OutlinedButton(onClick = onBack) { Text("Quay lại") } })
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    listOf("Lý thuyết", "Ví dụ", "Bài tập").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) {
                        0 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Pill(lesson.level)
                                Pill("${(lesson.content.split(Regex("\\s+")).size / 150 + 1)} phút", R.drawable.ic_clock)
                            }
                            Text(lesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            lesson.summary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFDDE4EB))) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Cách dùng", fontWeight = FontWeight.SemiBold)
                                    Text(lesson.content, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        1 -> if (lesson.examples.isEmpty()) EmptyState("Bài này chưa có ví dụ") else lesson.examples.forEach { ex ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFDDE4EB))) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(ex.en, fontWeight = FontWeight.SemiBold)
                                        Text(ex.vi, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { pronounce(ex.en, null) }, Modifier.size(36.dp).background(blueCard, RoundedCornerShape(50))) {
                                        Icon(painterResource(R.drawable.ic_volume), "Nghe câu ví dụ", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { practiceTarget = ex.en }, Modifier.size(36.dp).background(blueCard, RoundedCornerShape(50))) {
                                        Icon(painterResource(R.drawable.ic_mic), "Luyện nói câu ví dụ", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        else -> if (onPractice == null) EmptyState("Bài tập đang được chuẩn bị", "Phần bài tập của chủ điểm này sẽ sớm có.")
                        else EmptyState("Luyện tập chủ điểm này", "Tối đa 10 câu trắc nghiệm và điền từ, có giải thích sau mỗi câu.",
                            action = { Button(onClick = { onPractice(lesson.id) }) { Text("Làm bài tập") } })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Pronunciation practice: open the examples and start recording on the first one; each example also has its own mic button.
                    OutlinedButton(onClick = { tab = 1; practiceTarget = lesson.examples.firstOrNull()?.en }, enabled = lesson.examples.isNotEmpty(), modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Luyện phát âm", maxLines = 1)
                    }
                    Button(onClick = { onPractice?.invoke(lesson.id) }, enabled = onPractice != null, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(painterResource(R.drawable.ic_bolt), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Làm quiz", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable private fun Pill(text: String, icon: Int? = null) {
    Row(Modifier.background(chipBg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(painterResource(icon), null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
