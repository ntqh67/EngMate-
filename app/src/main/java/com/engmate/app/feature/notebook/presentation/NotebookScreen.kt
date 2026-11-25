package com.engmate.app.feature.notebook.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.notebook.domain.Handbook
import com.engmate.app.feature.notebook.domain.HandbookEntry
import com.engmate.app.feature.notebook.domain.NotebookRules

/** Sổ tay: the learner's handbooks, then the words of one handbook with search, status and swipe-to-remove. */
@Composable
fun NotebookScreen(viewModel: NotebookViewModel, onOpenWord: (String) -> Unit = {}, onStudy: (Long) -> Unit = {},
                   onFindWords: () -> Unit = {}, onBrowseTopics: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val removed by viewModel.removed.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)
    LaunchedEffect(removed) {
        val r = removed ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Đã gỡ “${r.word}” khỏi sổ tay", actionLabel = "Hoàn tác", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove() else viewModel.removedShown()
    }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = state.opened != null) { viewModel.close() }

    val opened = state.opened
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (opened != null && state.entries.isNotEmpty()) StickyBottomBar {
                PrimaryButton("Ôn tập ${state.entries.size} thẻ", { onStudy(opened.id) }, Modifier.fillMaxWidth().testTag("study_handbook"), icon = R.drawable.ic_layers)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (opened == null) HandbookList(state, viewModel::open, viewModel::showCreate, onFindWords, onBrowseTopics)
            else HandbookDetail(opened, state.entries, query, viewModel::search, viewModel::close, onDelete = { confirmDelete = true },
                onOpenWord = onOpenWord, onRemove = viewModel::removeWord, onFindWords = onFindWords)
        }
    }

    dialog?.let { d ->
        AppDialog("Tạo sổ tay mới", viewModel::dismissCreate, "Tạo", viewModel::create, busy = d.busy) {
            OutlinedTextField(d.title, viewModel::onTitleChange, Modifier.fillMaxWidth().testTag("handbook_title"),
                label = { Text("Tên sổ tay") }, placeholder = { Text("Từ vựng phỏng vấn") }, singleLine = true,
                isError = d.error != null, enabled = !d.busy,
                supportingText = { Text(d.error ?: "Tối đa ${NotebookRules.MAX_TITLE} ký tự") })
        }
    }
    if (confirmDelete) state.opened?.let { h ->
        ConfirmDialog("Xoá “${h.title}”?", "${h.wordCount} từ sẽ được gỡ khỏi sổ tay này. Từ, ghi chú và tiến độ ôn của bạn vẫn được giữ.",
            "Xoá sổ tay", onConfirm = { confirmDelete = false; viewModel.deleteOpened() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun HandbookList(state: NotebookUiState, onOpen: (Long) -> Unit, onCreate: () -> Unit, onFindWords: () -> Unit, onBrowseTopics: () -> Unit) {
    val pad = screenPadding
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = pad, end = pad, top = Spacing.l, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sổ tay", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TonalButton("Sổ tay mới", onCreate, Modifier.testTag("create_handbook"), icon = R.drawable.ic_add)
            }
        }
        when {
            state.loading -> item(key = "loading") { LoadingState(rows = 3, hero = false) }
            state.failed -> item(key = "error") { ErrorState("Không tải được sổ tay. Hãy mở lại ứng dụng.") }
            state.handbooks.isEmpty() -> item(key = "empty") {
                EmptyState("Gom từ muốn nhớ vào Sổ tay", "Tra một từ hoặc mở một chủ đề, rồi bấm Thêm vào Sổ tay để ôn bằng flashcard.",
                    icon = R.drawable.ic_notebook, action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PrimaryButton("Tra từ", onFindWords, icon = R.drawable.ic_search)
                            TextButton(onClick = onBrowseTopics) { Text("Xem chủ đề từ vựng") }
                        }
                    })
            }
            else -> item(key = "list") {
                SurfaceGroup {
                    state.handbooks.forEachIndexed { i, h ->
                        if (i > 0) GroupDivider()
                        HandbookRow(h) { onOpen(h.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandbookRow(handbook: Handbook, onClick: () -> Unit) {
    ListRow(handbook.title, supporting = "${handbook.wordCount} từ · ${handbook.knownCount} đã thuộc", onClick = onClick, headlineLines = 1,
        leading = {
            ProgressRing(if (handbook.wordCount == 0) 0f else handbook.knownCount / handbook.wordCount.toFloat(), size = 44.dp, strokeWidth = 5.dp) {
                Icon(painterResource(R.drawable.ic_notebook), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        },
        trailing = { RowChevron() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandbookDetail(handbook: Handbook, entries: List<HandbookEntry>, query: String, onQuery: (String) -> Unit, onBack: () -> Unit,
                           onDelete: () -> Unit, onOpenWord: (String) -> Unit, onRemove: (String) -> Unit, onFindWords: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(handbook.title, onBack) {
            Box {
                TopBarAction(R.drawable.ic_more, "Tuỳ chọn sổ tay", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Xoá sổ tay", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
        if (entries.isEmpty()) EmptyState("Sổ tay còn trống", "Tra từ rồi thêm vào sổ tay này.", icon = R.drawable.ic_notebook,
            action = { PrimaryButton("Tra từ", onFindWords, icon = R.drawable.ic_search) })
        else {
            val visible = filterEntries(entries, query)
            LazyColumn(contentPadding = PaddingValues(horizontal = screenPadding, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                item(key = "summary") {
                    LinearProgressWithLabel(if (handbook.wordCount == 0) 0f else handbook.knownCount / handbook.wordCount.toFloat(),
                        "${handbook.wordCount} từ · ${handbook.knownCount} đã thuộc")
                }
                item(key = "search") { AppSearchField(query, onQuery, "Tìm trong sổ tay") }
                if (visible.isEmpty()) item(key = "none") {
                    Text("Không có từ nào khớp “$query”.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.l))
                }
                items(visible, key = { it.cardId }) { entry -> SwipeRow(entry, onOpenWord, onRemove) }
                item(key = "hint") {
                    Text("Vuốt sang trái để gỡ một từ khỏi sổ tay.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.s))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(entry: HandbookEntry, onOpenWord: (String) -> Unit, onRemove: (String) -> Unit) {
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) { onRemove(entry.word); dismiss.snapTo(SwipeToDismissBoxValue.Settled) }
    }
    SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = false,
        modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Gỡ khỏi sổ tay") { onRemove(entry.word); true }) },
        backgroundContent = {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.large).padding(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Gỡ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(Spacing.s))
                Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }) {
        InfoCard(onClick = { onOpenWord(entry.word) }, contentPadding = PaddingValues(0.dp)) {
            ListRow(entry.word, supporting = entry.displayMeaning, headlineLines = 1,
                trailing = {
                    when (entry.status) {
                        WordLearningStatus.MASTERED -> StatusTag(ItemStatus.DONE, "Đã thuộc")
                        WordLearningStatus.LEARNING, WordLearningStatus.REVIEW -> StatusTag(ItemStatus.IN_PROGRESS, "Đang học")
                        else -> StatusTag(ItemStatus.NEW, "Mới")
                    }
                })
        }
    }
}
