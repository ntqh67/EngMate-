package com.engmate.app.feature.vocabulary.presentation.worddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.notebook.domain.NotebookRules
import com.engmate.app.feature.notebook.domain.WordExample
import com.engmate.app.feature.pronunciation.presentation.PronunciationSheet
import com.engmate.app.feature.vocabulary.domain.Word

@Composable
fun WordDetailScreen(viewModel: WordDetailViewModel, word: String, onBack: () -> Unit, onPronunciationScored: (Int) -> Unit = {}) {
    var speaking by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }
    LaunchedEffect(word) { viewModel.open(word) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)
    val pronounce = rememberPronouncer()

    Scaffold(
        topBar = {
            AppTopBar(onBack = onBack) {
                if (state.word != null) Box {
                    TextButton(onClick = { editMenu = true }) {
                        Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs)); Text("Sửa")
                    }
                    DropdownMenu(editMenu, { editMenu = false }) {
                        listOf(EditSheet.MEANING to "Nghĩa của bạn", EditSheet.EXAMPLE to "Thêm ví dụ", EditSheet.COLLOCATION to "Thêm cụm từ",
                            EditSheet.NOTE to "Ghi chú của tôi").forEach { (kind, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { editMenu = false; viewModel.showSheet(kind) })
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            state.word?.let {
                StickyBottomBar {
                    val saved = state.savedTitles
                    val onClick = { viewModel.showSheet(EditSheet.HANDBOOKS) }
                    if (saved.isEmpty()) PrimaryButton("Thêm vào Sổ tay", onClick, Modifier.fillMaxWidth().testTag("save_word"), icon = R.drawable.ic_add)
                    else SecondaryButton(if (saved.size == 1) "Đã lưu vào ${saved[0]}" else "Đã lưu vào ${saved.size} sổ tay", onClick,
                        Modifier.fillMaxWidth().testTag("save_word"), icon = R.drawable.ic_check)
                }
            }
        },
    ) { padding ->
        val w = state.word
        when {
            state.loading -> LoadingState(Modifier.padding(padding).padding(screenPadding), rows = 3)
            w == null -> Box(Modifier.padding(padding)) {
                EmptyState("Từ này chưa có trong máy", "Hãy tra lại từ khi có kết nối Internet.", icon = R.drawable.ic_wifi_off,
                    action = { SecondaryButton("Quay lại", onBack) })
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = screenPadding)
                .padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Header(w, state.customMeaning, onListen = { pronounce(w.word, w.audioUrl) }, onSpeak = { speaking = true },
                    onAddMeaning = { viewModel.showSheet(EditSheet.MEANING) })
                Details(w, state, viewModel)
                if (w.isCached) Text("Nguồn: Wiktionary · CC BY-SA 3.0 · qua dictionaryapi.dev", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val target = state.word
    if (speaking && target != null) PronunciationSheet(target.word, target.phonetic, onDismiss = { speaking = false }, onScored = onPronunciationScored)
    sheet?.let { EditSheetContent(it, state, viewModel) }
}

@Composable
private fun Header(word: Word, customMeaning: String?, onListen: () -> Unit, onSpeak: () -> Unit, onAddMeaning: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(word.word, style = EngMateTheme.type.vocabulary, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            word.phonetic?.let { IpaText(it) } ?: Text("Chưa có phiên âm", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            word.partOfSpeech?.let { Text("· $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TonalButton("Nghe", onListen, icon = R.drawable.ic_volume)
            TonalButton("Luyện nói", onSpeak, icon = R.drawable.ic_mic)
        }
        val meaning = customMeaning ?: word.meaningVi
        if (meaning != null) {
            Text(meaning, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.s))
            if (customMeaning != null) Text("Nghĩa của bạn", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        } else FeedbackBanner("Từ điển trực tuyến chỉ có tiếng Anh. Thêm nghĩa của bạn để ôn flashcard dễ hơn.", title = "Chưa có nghĩa tiếng Việt",
            action = { TextButton(onClick = onAddMeaning, contentPadding = PaddingValues(0.dp)) { Text("Thêm nghĩa của bạn") } })
    }
}

/** Definition, examples, collocations and the personal note on one surface, separated by dividers. */
@Composable
private fun Details(word: Word, state: WordDetailState, viewModel: WordDetailViewModel) {
    val note = state.note
    SurfaceGroup {
        Section("Định nghĩa") { Text(word.meaningEn, style = MaterialTheme.typography.bodyLarge) }
        GroupDivider()
        val mine = note?.examples.orEmpty()
        val dictionaryExample = word.exampleSentence?.takeIf { mine.size < 3 }?.let { WordExample(it, word.exampleMeaning.orEmpty()) }
        Section("Ví dụ") {
            dictionaryExample?.let { Example(it, mine = false) }
            mine.forEachIndexed { i, ex -> Example(ex, mine = true) { viewModel.removeExample(i) } }
            AddLink("Thêm ví dụ") { viewModel.showSheet(EditSheet.EXAMPLE) }
        }
        GroupDivider()
        val custom = note?.collocations.orEmpty()
        Section("Cụm từ đi kèm") {
            if (word.collocations.isNotEmpty() || custom.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                word.collocations.forEach { Tag(it) }
                custom.forEachIndexed { i, c -> Tag(c, onRemove = { viewModel.removeCollocation(i) }) }
            }
            AddLink("Thêm cụm từ") { viewModel.showSheet(EditSheet.COLLOCATION) }
        }
        GroupDivider()
        Section("Ghi chú của tôi") {
            note?.personalNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { viewModel.showSheet(EditSheet.NOTE) }) }
                ?: AddLink("Thêm ghi chú") { viewModel.showSheet(EditSheet.NOTE) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun AddLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs)); Text(label)
    }
}

@Composable
private fun Example(example: WordExample, mine: Boolean, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (mine) Text("Của bạn", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(example.en, style = MaterialTheme.typography.bodyLarge)
            if (example.vi.isNotBlank()) Text(example.vi, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onRemove?.let { IconButton(onClick = it) { Icon(painterResource(R.drawable.ic_close), "Xoá ví dụ này", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
private fun Tag(text: String, onRemove: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.background(if (onRemove != null) c.secondaryContainer else c.primaryContainer, MaterialTheme.shapes.small)
        .padding(start = Spacing.m, end = if (onRemove != null) 0.dp else Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (onRemove != null) c.onSecondaryContainer else c.onPrimaryContainer,
            modifier = Modifier.padding(vertical = Spacing.xs))
        if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_close), "Xoá cụm “$text”", Modifier.size(16.dp), tint = c.onSecondaryContainer)
        }
    }
}

@Composable
private fun EditSheetContent(sheet: SheetState, state: WordDetailState, viewModel: WordDetailViewModel) {
    val word = state.word?.word.orEmpty()
    val title = when (sheet.kind) {
        EditSheet.MEANING -> "Nghĩa của bạn"; EditSheet.EXAMPLE -> "Thêm ví dụ"; EditSheet.COLLOCATION -> "Thêm cụm từ đi kèm"
        EditSheet.NOTE -> "Ghi chú của tôi"; EditSheet.HANDBOOKS -> "Lưu “$word” vào Sổ tay"
    }
    AppBottomSheet(title, viewModel::dismissSheet) {
        var first by remember(sheet.kind) {
            mutableStateOf(when (sheet.kind) { EditSheet.MEANING -> state.customMeaning.orEmpty(); EditSheet.NOTE -> state.note?.personalNote.orEmpty(); else -> "" })
        }
        var second by remember(sheet.kind) { mutableStateOf("") }
        @Composable fun Field(value: String, label: String, max: Int, lines: Int = 1, set: (String) -> Unit) =
            OutlinedTextField(value, { set(it.take(max)); viewModel.clearError() }, Modifier.fillMaxWidth(), label = { Text(label) },
                singleLine = lines == 1, minLines = lines, enabled = !sheet.busy, shape = MaterialTheme.shapes.medium,
                supportingText = { Text("${value.length}/$max") })
        val muted: @Composable (String) -> Unit = { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (sheet.kind) {
            EditSheet.MEANING -> { muted("Để trống rồi Lưu để dùng lại nghĩa gốc."); Field(first, "Nghĩa tiếng Việt", NotebookRules.MAX_MEANING) { first = it } }
            EditSheet.EXAMPLE -> { Field(first, "Câu tiếng Anh của bạn", NotebookRules.MAX_EXAMPLE) { first = it }; Field(second, "Bản dịch (không bắt buộc)", NotebookRules.MAX_EXAMPLE) { second = it } }
            EditSheet.COLLOCATION -> Field(first, "Cụm từ", NotebookRules.MAX_COLLOCATION) { first = it }
            EditSheet.NOTE -> Field(first, "Mẹo nhớ, ngữ cảnh hay gặp…", NotebookRules.MAX_NOTE, lines = 3) { first = it }
            EditSheet.HANDBOOKS -> {
                muted("Chọn một hoặc nhiều sổ tay.")
                SurfaceGroup {
                    state.handbooks.forEachIndexed { i, h ->
                        if (i > 0) GroupDivider(56.dp)
                        ListRow(h.title, supporting = "${h.wordCount} từ", headlineLines = 1, onClick = if (sheet.busy) null else ({ viewModel.toggleHandbook(h.id) }),
                            leading = { Checkbox(h.id in state.savedIn, null, enabled = !sheet.busy) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Box(Modifier.weight(1f)) { Field(first, "Hoặc tạo sổ tay mới", NotebookRules.MAX_TITLE) { first = it } }
                    TonalButton("Tạo", { viewModel.createHandbookAndAdd(first); first = "" }, enabled = !sheet.busy && first.isNotBlank())
                }
            }
        }
        sheet.error?.let { FieldError(it) }
        if (sheet.kind == EditSheet.HANDBOOKS) PrimaryButton("Xong", viewModel::dismissSheet, Modifier.fillMaxWidth())
        else ButtonPair("Hủy", viewModel::dismissSheet, "Lưu", {
            when (sheet.kind) {
                EditSheet.MEANING -> viewModel.saveMeaning(first); EditSheet.EXAMPLE -> viewModel.addExample(first, second)
                EditSheet.COLLOCATION -> viewModel.addCollocation(first); EditSheet.NOTE -> viewModel.saveNote(first); else -> Unit
            }
        }, loading = sheet.busy)
    }
}
