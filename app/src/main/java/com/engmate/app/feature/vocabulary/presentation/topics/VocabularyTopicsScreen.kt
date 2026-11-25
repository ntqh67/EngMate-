package com.engmate.app.feature.vocabulary.presentation.topics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.vocabulary.domain.Topic
import com.engmate.app.feature.vocabulary.domain.Word
import com.engmate.app.feature.vocabulary.domain.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VocabularyTopicsViewModel @Inject constructor(private val words: WordRepository) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    val topics: StateFlow<List<Topic>?> = words.observeTopics().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val topicWords: StateFlow<List<Word>> = selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else words.observeTopicWords(id) }
        .catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun open(id: Long?) { selected.value = id }
}

/** Học → Từ vựng: topics as a two-column grid, then the words of a topic. Tapping a word opens its detail page. */
@Composable
fun VocabularyTopicsScreen(viewModel: VocabularyTopicsViewModel, onOpenWord: (String) -> Unit) {
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val wordList by viewModel.topicWords.collectAsStateWithLifecycle()
    var openTopic by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(openTopic) { viewModel.open(openTopic) }
    val current = topics?.firstOrNull { it.id == openTopic }
    val pronounce = rememberPronouncer()
    val pad = screenPadding
    if (current != null) {
        BackHandler { openTopic = null }
        Column(Modifier.fillMaxSize()) {
            AppTopBar(current.title, onBack = { openTopic = null })
            if (wordList.isEmpty()) EmptyState("Chủ đề chưa có từ", "Từ của chủ đề này đang được nạp, hãy quay lại sau.", icon = TOPIC_ICON)
            else LazyColumn(contentPadding = PaddingValues(start = pad, end = pad, bottom = Spacing.xl)) {
                item(key = "summary") {
                    Text("${wordList.size} từ" + (current.description?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.m))
                }
                item(key = "words") {
                    SurfaceGroup {
                        wordList.forEachIndexed { i, w ->
                            if (i > 0) GroupDivider()
                            ListRow(w.word, supporting = w.meaningVi ?: w.meaningEn, headlineLines = 1, onClick = { onOpenWord(w.word) },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        w.phonetic?.let { IpaText(it) }
                                        IconButton(onClick = { pronounce(w.word, w.audioUrl) }) {
                                            Icon(painterResource(R.drawable.ic_volume), "Nghe “${w.word}”", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                })
                        }
                    }
                }
            }
        }
        return
    }
    when {
        topics == null -> LoadingState(Modifier.padding(pad), rows = 4, hero = false)
        topics.orEmpty().isEmpty() -> EmptyState("Chưa có chủ đề từ vựng", "Bộ từ khởi đầu đang được nạp, hãy thử lại sau giây lát.", icon = TOPIC_ICON)
        else -> {
            val list = topics.orEmpty()
            LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(start = pad, end = pad, top = Spacing.l, bottom = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                    Text("${list.size} chủ đề · ${list.sumOf { it.wordCount }} từ", style = MaterialTheme.typography.titleSmall)
                }
                itemsIndexed(list, key = { _, t -> t.id }) { i, t -> TopicTile(t, i) { openTopic = t.id } }
            }
        }
    }
}

private val TOPIC_ICON = R.drawable.ic_book

@Composable
private fun TopicTile(topic: Topic, index: Int, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    // Cycle three tints so neighbouring tiles are told apart without inventing per-topic art.
    val tint = listOf(c.primary, c.tertiary, c.secondary)[index % 3]
    InfoCard(Modifier.heightIn(min = 120.dp), onClick = onClick) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.14f), MaterialTheme.shapes.medium), Alignment.Center) {
            Icon(painterResource(TOPIC_ICON), null, Modifier.size(22.dp), tint = tint)
        }
        Text(topic.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${topic.wordCount} từ", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}
