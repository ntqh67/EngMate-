package com.engmate.app.feature.grammar.presentation.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.grammar.presentation.topics.GrammarLessonsViewModel

/** One scrolling page per lesson; [onPractice] starts its quiz, null disables the button. */
@Composable
fun GrammarLessonScreen(viewModel: GrammarLessonsViewModel, lessonId: Long, onBack: () -> Unit, onPractice: ((Long) -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lesson = state.lesson(lessonId)
    val questionCount by produceState<Int?>(null, lessonId) { value = viewModel.questionCount(lessonId) }
    Scaffold(
        topBar = { AppTopBar(onBack = onBack) },
        bottomBar = {
            if (lesson != null) StickyBottomBar {
                val count = questionCount
                PrimaryButton(
                    text = when (count) { null -> "Làm bài tập"; 0 -> "Bài này chưa có bài tập"; else -> "Làm bài tập ($count câu)" },
                    onClick = { onPractice?.invoke(lesson.id) },
                    enabled = onPractice != null && count != 0,
                    icon = R.drawable.ic_bolt,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding).padding(screenPadding), rows = 4)
            lesson == null -> Box(Modifier.padding(padding)) {
                EmptyState("Bài học không còn tồn tại", "Có thể bài đã được gỡ khỏi danh sách.", icon = R.drawable.ic_assignment,
                    action = { SecondaryButton("Quay lại", onBack) })
            }
            else -> LessonBody(lesson, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LessonBody(lesson: GrammarLesson, modifier: Modifier) {
    val blocks = remember(lesson.content) { LessonContent.parse(lesson.content) }
    val (vi, en) = remember(lesson.title) { LessonContent.splitTitle(lesson.title) }
    val pronounce = rememberPronouncer()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = screenPadding)
        .padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                LevelChip(lesson.level)
                Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${LessonContent.readingMinutes(lesson.content)} phút đọc", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(vi, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (en != null) Text(en, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            lesson.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        blocks.forEach { block ->
            when (block) {
                is LessonBlock.Formula -> FormulaBlock(block)
                is LessonBlock.Points -> Section(block.title) {
                    block.lead?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    block.items.forEach { Bullet(it) }
                }
                is LessonBlock.Markers -> Section("Dấu hiệu nhận biết") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        block.items.forEach { Text(it, Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                            .padding(horizontal = Spacing.m, vertical = Spacing.xs), style = MaterialTheme.typography.labelLarge) }
                    }
                }
                is LessonBlock.Note -> NoteBlock(block)
                is LessonBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (lesson.examples.isNotEmpty()) Section("Ví dụ") {
            SurfaceGroup {
                lesson.examples.forEachIndexed { i, ex ->
                    if (i > 0) GroupDivider()
                    ExampleRow(ex) { pronounce(ex.en, null) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.padding(top = 10.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FormulaBlock(block: LessonBlock.Formula) {
    val brand = EngMateTheme.brand
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = brand.brand, contentColor = brand.onBrand) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("Công thức", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            block.rows.forEach { (label, formula) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (label != null) Text(label, style = MaterialTheme.typography.labelMedium)
                    // "S + V(s/es). Với he/she/it, …": the formula in bold, the explanation after it in plain body text.
                    val cut = formula.indexOf(". ")
                    Text(if (cut > 0) formula.substring(0, cut + 1) else formula, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (cut > 0) Text(formula.substring(cut + 2), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NoteBlock(block: LessonBlock.Note) {
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = c.secondaryContainer, contentColor = c.onSecondaryContainer) {
        Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(R.drawable.ic_lightbulb), null, Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(block.title, style = MaterialTheme.typography.titleSmall)
                if (block.text.isNotBlank()) Text(block.text, style = MaterialTheme.typography.bodyMedium)
                block.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun ExampleRow(example: GrammarExample, onListen: () -> Unit) {
    Row(Modifier.padding(start = Spacing.l, end = Spacing.xs, top = Spacing.m, bottom = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(example.en, style = MaterialTheme.typography.titleMedium)
            if (example.vi.isNotBlank()) Text(example.vi, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onListen) { Icon(painterResource(R.drawable.ic_volume), "Nghe câu ví dụ", tint = MaterialTheme.colorScheme.primary) }
    }
}
