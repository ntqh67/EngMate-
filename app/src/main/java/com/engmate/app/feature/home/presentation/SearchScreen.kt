package com.engmate.app.feature.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.vocabulary.domain.Word

/**
 * Tra từ: the dictionary search that used to sit on the home screen, unchanged in behavior
 * (local words first, then a dictionaryapi.dev preview saved only when opened).
 */
@Composable
fun SearchScreen(viewModel: HomeViewModel, onBack: () -> Unit, onOpenWord: (Word) -> Unit) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    SearchContent(search, recent, viewModel::onQueryChange, onBack) { viewModel.open(it, onOpenWord) }
}

@Composable
internal fun SearchContent(search: SearchState, recent: List<String>, onQueryChange: (String) -> Unit, onBack: () -> Unit, onOpen: (Word) -> Unit) {
    Scaffold(topBar = {
        Row(Modifier.statusBarsPadding().padding(end = screenPadding, top = Spacing.s, bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Quay lại") }
            AppSearchField(search.query, onQueryChange, "Tìm từ tiếng Anh…", Modifier.weight(1f).testTag("home_search_field"), autoFocus = true)
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            if (search.searching) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("search_progress")) }
            if (search.query.isBlank()) {
                if (recent.isNotEmpty()) item(key = "recent") {
                    Column(Modifier.padding(horizontal = screenPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        SectionHeader("Tìm gần đây")
                        ChipRow(recent, null, { it }, onQueryChange)
                    }
                }
                item(key = "hint") {
                    EmptyState("Tra một từ tiếng Anh", "Xem nghĩa, phát âm, ví dụ và lưu vào Sổ tay để ôn sau.", icon = R.drawable.ic_search)
                }
            } else {
                search.notice?.let { notice ->
                    item(key = "notice") {
                        FeedbackBanner(notice, Modifier.padding(horizontal = screenPadding, vertical = Spacing.s),
                            kind = if (notice.startsWith("Không có kết nối")) FeedbackKind.WARNING else FeedbackKind.INFO)
                    }
                }
                items(search.results, key = { "${it.id}:${it.word}" }) { word ->
                    ListRow(word.word, supporting = word.meaningVi ?: word.meaningEn, headlineLines = 1, onClick = { onOpen(word) },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                word.phonetic?.let { IpaText(it) }
                                word.partOfSpeech?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        })
                    HorizontalDivider(Modifier.padding(horizontal = screenPadding), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
