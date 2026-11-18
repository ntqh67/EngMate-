package com.engmate.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.profile.domain.ProfileRepository
import com.engmate.app.feature.profile.domain.UserProfile
import com.engmate.app.feature.quiz.domain.AttemptSummary
import com.engmate.app.feature.quiz.domain.QuizAttemptRepository
import com.engmate.app.feature.dictionary.domain.DictionaryRepository
import com.engmate.app.feature.dictionary.domain.LookupResult
import com.engmate.app.feature.vocabulary.domain.Word
import com.engmate.app.feature.vocabulary.domain.WordKey
import com.engmate.app.feature.vocabulary.domain.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val profile: UserProfile? = null,
    val lessons: List<GrammarLesson> = emptyList(),
    val attempts: List<AttemptSummary> = emptyList(),
    val failed: Boolean = false,
) {
    /** First lesson (by order) the learner has not practised yet, else the first lesson. */
    val nextLesson: GrammarLesson? get() {
        val done = attempts.filterNot { it.isPlacement }.map { it.title }.toSet()
        val ordered = lessons.sortedBy { it.orderIndex }
        return ordered.firstOrNull { it.title !in done } ?: ordered.firstOrNull()
    }
}

/** [notice] explains an empty or partial result: not found, offline, or a failed lookup. */
data class SearchState(val query: String = "", val results: List<Word> = emptyList(), val searching: Boolean = false, val notice: String? = null)

/**
 * Local matches first; when the exact word is not on the device, ask the dictionary and put the hit on top.
 * The hit is only previewed: it is saved when the learner opens it ([HomeViewModel.open]).
 * A word the dictionary does not know is not shown.
 */
internal fun searchFlow(q: String, words: WordRepository, dictionary: DictionaryRepository): Flow<SearchState> = flow {
    val key = WordKey.of(q)
    if (key.isEmpty()) { emit(SearchState(q)); return@flow }
    val local = runCatching { words.search(key) }.getOrDefault(emptyList())
    if (local.any { it.word == key } || !DictionaryRepository.canLookup(key)) {
        emit(SearchState(q, local, notice = if (local.isEmpty()) "Không tìm thấy “$key”." else null)); return@flow
    }
    emit(SearchState(q, local, searching = true))
    emit(when (val result = dictionary.preview(key)) {
        is LookupResult.Found -> SearchState(q, listOf(result.word) + local.filter { it.id != result.word.id })
        LookupResult.NotFound -> SearchState(q, local, notice = if (local.isEmpty()) "Không tìm thấy “$key”." else null)
        LookupResult.Offline -> SearchState(q, local, notice = "Không có kết nối Internet. Đang hiện từ đã lưu trong máy.")
        LookupResult.Failed -> SearchState(q, local, notice = "Không tra được từ điển trực tuyến. Hãy thử lại.")
    })
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    session: UserSession,
    profiles: ProfileRepository,
    attempts: QuizAttemptRepository,
    grammar: GrammarRepository,
    private val words: WordRepository,
    private val dictionary: DictionaryRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = session.user.flatMapLatest { user ->
        if (user == null) grammar.observeLessons().map { HomeUiState(lessons = it) }
        else combine(profiles.observe(user.uid), attempts.observeHistory(user.uid), grammar.observeLessons()) { p, a, l ->
            HomeUiState(profile = p, lessons = l, attempts = a)
        }
    }.catch { emit(HomeUiState(failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val query = MutableStateFlow("")

    // 400 ms debounce: one API call per pause in typing, not per keystroke.
    val search: StateFlow<SearchState> = query.debounce(400).flatMapLatest { q -> searchFlow(q, words, dictionary) }
        .combine(query) { found, typed ->
        // Typing shows "searching" until the debounced result for the same text arrives.
        if (found.query == typed) found else found.copy(query = typed, searching = typed.isNotBlank())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun onQueryChange(value: String) { openError.value = null; query.value = value.take(50) }

    private val opening = MutableStateFlow(false)
    private val openError = MutableStateFlow<String?>(null)

    /** Search state plus the progress and error of opening a word. */
    val searchWithOpen: StateFlow<SearchState> = combine(search, opening, openError) { s, busy, error ->
        s.copy(searching = s.searching || busy, notice = error ?: s.notice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    /** Saves a dictionary hit on the device (only now, when it is opened), then calls [onReady] with the stored word. */
    fun open(word: Word, onReady: (Word) -> Unit) {
        if (word.id != DictionaryRepository.UNSAVED_ID) { onReady(word); return }
        if (opening.value) return
        opening.value = true
        openError.value = null
        viewModelScope.launch {
            val result = try { dictionary.lookup(word.word) } finally { opening.value = false }
            when (result) {
                is LookupResult.Found -> onReady(result.word)
                LookupResult.NotFound -> openError.value = "Không tìm thấy “${word.word}”."
                LookupResult.Offline -> openError.value = "Không có kết nối Internet. Hãy thử lại."
                LookupResult.Failed -> openError.value = "Không lưu được từ này. Hãy thử lại."
            }
        }
    }
}
