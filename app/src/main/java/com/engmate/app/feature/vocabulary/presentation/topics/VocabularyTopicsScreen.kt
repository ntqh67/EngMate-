package com.engmate.app.feature.vocabulary.presentation.topics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.EmptyState
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

/** Học → Từ vựng: topics first, then the words of a topic. Tapping a word opens its detail page. */
@Composable
fun VocabularyTopicsScreen(viewModel: VocabularyTopicsViewModel, onOpenWord: (String) -> Unit) {
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val wordList by viewModel.topicWords.collectAsStateWithLifecycle()
    var openTopic by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(openTopic) { viewModel.open(openTopic) }
    val current = topics?.firstOrNull { it.id == openTopic }
    if (current != null) {
        BackHandler { openTopic = null }
        Column(Modifier.fillMaxSize()) {
            BackTopBar(current.title, onBack = { openTopic = null })
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wordList, key = { it.id }) { w ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenWord(w.word) }, shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFDDE4EB))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(buildString { append(w.word); w.phonetic?.let { append("  $it") } }, fontWeight = FontWeight.SemiBold)
                            Text(w.meaningVi ?: w.meaningEn, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        return
    }
    when {
        topics == null -> Box(Modifier.fillMaxSize(), androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        topics.orEmpty().isEmpty() -> EmptyState("Chưa có chủ đề từ vựng", "Bộ từ khởi đầu đang được nạp, hãy thử lại sau giây lát.")
        else -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Từ vựng theo chủ đề", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            items(topics.orEmpty(), key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { openTopic = t.id }, shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFDDE4EB))) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t.title, fontWeight = FontWeight.SemiBold)
                        Text("${t.wordCount} từ" + (t.description?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
