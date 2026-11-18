package com.engmate.app.feature.vocabulary.presentation.worddetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.EmptyState
import com.engmate.app.core.ui.components.MessageEffect
import com.engmate.app.core.ui.components.rememberPronouncer
import com.engmate.app.feature.notebook.domain.NotebookRules
import com.engmate.app.feature.notebook.domain.WordExample

private val line = Color(0xFFDDE4EB)
private val quoteBg = Color(0xFFF0F8FF)
private val mangoBg = Color(0xFFFFF2EC)
private val mango = Color(0xFF803D09)

@Composable
fun WordDetailScreen(viewModel: WordDetailViewModel, word: String, onBack: () -> Unit, onPronunciationScored: (Int) -> Unit = {}) {
    var speaking by remember { mutableStateOf(false) }
    LaunchedEffect(word) { viewModel.open(word) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)
    val pronounce = rememberPronouncer()

    Scaffold(topBar = { BackTopBar("Chi tiết từ vựng", onBack) }, snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            state.word?.let {
                Surface(tonalElevation = 2.dp) {
                    val saved = state.savedTitles
                    val label = when (saved.size) { 0 -> "Thêm vào Vocab Handbook"; 1 -> "Đã lưu trong ${saved[0]} · Quản lý"; else -> "Đã lưu trong ${saved.size} sổ tay · Quản lý" }
                    val onClick = { viewModel.showSheet(EditSheet.HANDBOOKS) }
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        if (saved.isEmpty()) Button(onClick, Modifier.fillMaxWidth().height(48.dp).testTag("save_word")) { Text("+ $label") }
                        else OutlinedButton(onClick, Modifier.fillMaxWidth().height(48.dp).testTag("save_word")) { Text("✓ $label", maxLines = 1) }
                    }
                }
            }
        }) { padding ->
        val word = state.word
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            word == null -> Box(Modifier.padding(padding)) {
                EmptyState("Từ này chưa có trong máy", "Hãy tra lại từ ở Trang chủ khi có kết nối Internet.",
                    action = { OutlinedButton(onClick = onBack) { Text("Quay lại") } })
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val note = state.note
                // Header: word, IPA, audio and the meaning to remember.
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text(word.word, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        word.partOfSpeech?.let { Chip(it.replaceFirstChar(Char::uppercase)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(word.phonetic ?: "Chưa có phiên âm", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = if (word.phonetic == null) FontStyle.Italic else FontStyle.Normal)
                        AssistChip(onClick = { pronounce(word.word, word.audioUrl) }, label = { Text("Nghe") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_volume), null, Modifier.size(16.dp)) })
                        AssistChip(onClick = { speaking = true }, label = { Text("Luyện nói") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(16.dp)) })
                    }
                    if (speaking) com.engmate.app.feature.pronunciation.presentation.PronunciationDialog(word.word, { speaking = false }, onPronunciationScored)
                    HorizontalDivider(color = line)
                    val meaning = state.customMeaning ?: word.meaningVi
                    if (meaning != null) Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(meaning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (state.customMeaning != null) Chip("Nghĩa của bạn", mangoBg, mango)
                        }
                        Pen("Sửa nghĩa") { viewModel.showSheet(EditSheet.MEANING) }
                    } else Column(Modifier.fillMaxWidth().background(mangoBg, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Chưa có nghĩa tiếng Việt", fontWeight = FontWeight.SemiBold, color = mango)
                        Text("Từ điển trực tuyến chỉ có tiếng Anh. Thêm nghĩa của bạn để ôn flashcard dễ hơn.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { viewModel.showSheet(EditSheet.MEANING) }, contentPadding = PaddingValues(0.dp)) {
                            Text("+ Thêm nghĩa của bạn", color = mango)
                        }
                    }
                }
                SectionCard("Định nghĩa") { Text(word.meaningEn, style = MaterialTheme.typography.bodyLarge) }

                val mine = note?.examples.orEmpty()
                val dictionaryExample = word.exampleSentence?.takeIf { mine.size < 3 }?.let { WordExample(it, word.exampleMeaning.orEmpty()) }
                SectionCard("Ví dụ minh họa", onEdit = { viewModel.showSheet(EditSheet.EXAMPLE) }) {
                    if (dictionaryExample == null && mine.isEmpty()) Muted("Từ điển không có ví dụ cho từ này. Bấm ✎ để thêm ví dụ của bạn.")
                    dictionaryExample?.let { Quote(it, mine = false) }
                    mine.forEachIndexed { i, ex -> Quote(ex, mine = true) { viewModel.removeExample(i) } }
                }

                val custom = note?.collocations.orEmpty()
                SectionCard("Cụm từ đi kèm", onEdit = { viewModel.showSheet(EditSheet.COLLOCATION) }) {
                    if (word.collocations.isEmpty() && custom.isEmpty()) Muted("Chưa có cụm từ. Bấm ✎ để thêm cụm bạn hay gặp.")
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        word.collocations.forEach { Chip(it) }
                        custom.forEachIndexed { i, c -> Chip("$c  ✕", mangoBg, mango, Modifier.clickable { viewModel.removeCollocation(i) }) }
                    }
                }

                SectionCard("Ghi chú của tôi", onEdit = { viewModel.showSheet(EditSheet.NOTE) }) {
                    note?.personalNote?.let { Text(it) } ?: Muted("Thêm ghi chú để nhớ từ lâu hơn…", Modifier.clickable { viewModel.showSheet(EditSheet.NOTE) })
                }
                if (word.isCached) Text("Nguồn: Wiktionary · CC BY-SA 3.0 · qua dictionaryapi.dev", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
    sheet?.let { EditSheetContent(it, state, viewModel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheetContent(sheet: SheetState, state: WordDetailState, viewModel: WordDetailViewModel) {
    ModalBottomSheet(onDismissRequest = viewModel::dismissSheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val word = state.word?.word.orEmpty()
            var first by remember(sheet.kind) {
                mutableStateOf(when (sheet.kind) { EditSheet.MEANING -> state.customMeaning.orEmpty(); EditSheet.NOTE -> state.note?.personalNote.orEmpty(); else -> "" })
            }
            var second by remember(sheet.kind) { mutableStateOf("") }
            fun field(value: String, label: String, max: Int, lines: Int = 1, set: (String) -> Unit) =
                @Composable { OutlinedTextField(value, { set(it.take(max)); viewModel.clearError() }, Modifier.fillMaxWidth(), label = { Text(label) },
                    singleLine = lines == 1, minLines = lines, enabled = !sheet.busy) }
            val title = when (sheet.kind) {
                EditSheet.MEANING -> "Nghĩa của bạn"; EditSheet.EXAMPLE -> "Thêm ví dụ"; EditSheet.COLLOCATION -> "Thêm cụm từ đi kèm"
                EditSheet.NOTE -> "Ghi chú của tôi"; EditSheet.HANDBOOKS -> "Lưu “$word” vào sổ tay"
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            when (sheet.kind) {
                EditSheet.MEANING -> { Muted("“$word” · để trống rồi Lưu để dùng lại nghĩa gốc."); field(first, "Nghĩa tiếng Việt", NotebookRules.MAX_MEANING) { first = it }() }
                EditSheet.EXAMPLE -> { field(first, "Câu tiếng Anh của bạn", NotebookRules.MAX_EXAMPLE) { first = it }(); field(second, "Bản dịch (không bắt buộc)", NotebookRules.MAX_EXAMPLE) { second = it }() }
                EditSheet.COLLOCATION -> field(first, "Cụm từ", NotebookRules.MAX_COLLOCATION) { first = it }()
                EditSheet.NOTE -> field(first, "Mẹo nhớ, ngữ cảnh hay gặp…", NotebookRules.MAX_NOTE, lines = 3) { first = it }()
                EditSheet.HANDBOOKS -> {
                    Muted("Chọn một hoặc nhiều sổ tay.")
                    state.handbooks.forEach { h ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !sheet.busy) { viewModel.toggleHandbook(h.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(h.id in state.savedIn, { viewModel.toggleHandbook(h.id) }, enabled = !sheet.busy)
                            Column { Text(h.title, fontWeight = FontWeight.Medium); Muted("${h.wordCount} từ") }
                        }
                    }
                    Text("Hoặc tạo sổ tay mới", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { field(first, "Tên sổ tay", NotebookRules.MAX_TITLE) { first = it }() }
                        FilledTonalButton(onClick = { viewModel.createHandbookAndAdd(first); first = "" }, enabled = !sheet.busy) { Text("Tạo") }
                    }
                }
            }
            sheet.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sheet.kind == EditSheet.HANDBOOKS) Button(viewModel::dismissSheet, Modifier.fillMaxWidth()) { Text("Xong") }
                else {
                    OutlinedButton(viewModel::dismissSheet, Modifier.weight(1f), enabled = !sheet.busy) { Text("Huỷ") }
                    Button(onClick = {
                        when (sheet.kind) {
                            EditSheet.MEANING -> viewModel.saveMeaning(first); EditSheet.EXAMPLE -> viewModel.addExample(first, second)
                            EditSheet.COLLOCATION -> viewModel.addCollocation(first); EditSheet.NOTE -> viewModel.saveNote(first); else -> Unit
                        }
                    }, Modifier.weight(1f), enabled = !sheet.busy) { Text("Lưu") }
                }
            }
        }
    }
}

@Composable private fun SectionCard(title: String? = null, onEdit: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                onEdit?.let { Pen("Sửa $title", it) }
            }
            content()
        }
    }
}

@Composable private fun Pen(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.size(32.dp).semantics { contentDescription = description }) { Text("✎", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable private fun Quote(example: WordExample, mine: Boolean, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(if (mine) mangoBg else quoteBg, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mine) Chip("Của bạn", Color.White, mango)
            Text("“${example.en}”", fontWeight = FontWeight.Medium)
            if (example.vi.isNotBlank()) Muted(example.vi)
        }
        onRemove?.let { TextButton(onClick = it, contentPadding = PaddingValues(0.dp)) { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable private fun Chip(text: String, bg: Color = Color(0xFFE1F0FF), fg: Color = Color(0xFF1A5998), modifier: Modifier = Modifier) {
    Text(text, modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = fg)
}

@Composable private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
