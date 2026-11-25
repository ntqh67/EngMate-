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
import com.engmate.app.core.database.ActivityType
import com.engmate.app.feature.flashcard.domain.ReviewRepository
import com.engmate.app.feature.progress.data.local.DailyStudyStatEntity
import com.engmate.app.feature.progress.data.local.LearningActivityEntity
import com.engmate.app.feature.progress.data.local.StudyDao
import com.engmate.app.feature.progress.presentation.calculateProgress
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the home dashboard shows. Display-only: every field comes from an existing repository or DAO read.
 * [dueCount] is null when the review count could not be read, so the card hides instead of showing 0.
 */
data class HomeUiState(
    val profile: UserProfile? = null,
    val lessons: List<GrammarLesson> = emptyList(),
    val attempts: List<AttemptSummary> = emptyList(),
    val failed: Boolean = false,
    val loading: Boolean = false,
    val signedIn: Boolean = false,
    val todayMinutes: Int = 0,
    val streak: Int = 0,
    val totalXp: Long = 0,
    val dueCount: Int? = null,
    /** Lesson of the most recent grammar study activity. */
    val lastLessonId: Long? = null,
    val hasActivity: Boolean = false,
) {
    private val ordered: List<GrammarLesson> get() = lessons.sortedBy { it.orderIndex }
    private val practised: Set<Long> get() = attempts.mapNotNull { it.lessonId }.toSet()

    /** First lesson (by order) the learner has not practised yet, else the first lesson. Matched by lessonId, not title. */
    val nextLesson: GrammarLesson? get() = ordered.firstOrNull { it.id !in practised } ?: ordered.firstOrNull()

    /** "Tiếp tục": the last opened lesson if it still exists, otherwise [nextLesson]. */
    val continueLesson: GrammarLesson? get() = lastLessonId?.let { id -> lessons.firstOrNull { it.id == id } } ?: nextLesson

    /** Best score in percent over the attempts of a lesson, or null when it was never practised. */
    fun bestPercent(lessonId: Long): Int? = attempts.filter { it.lessonId == lessonId && it.totalQuestions > 0 }
        .maxOfOrNull { Math.round(it.correctCount * 100f / it.totalQuestions) }

    val goalMinutes: Int? get() = profile?.dailyGoalMinutes?.takeIf { it > 0 }
    val goalReached: Boolean get() = goalMinutes?.let { todayMinutes >= it } == true

    /** Up to three unpractised lessons at the learner's level, not repeating the "Tiếp tục" lesson. */
    val suggestions: List<GrammarLesson> get() {
        val level = profile?.englishLevel ?: return emptyList()
        return ordered.filter { it.level == level && it.id !in practised && it.id != continueLesson?.id }.take(3)
    }

    /** Three most recent attempts. */
    val recent: List<AttemptSummary> get() = attempts.sortedByDescending { it.completedAt }.take(3)

    /** Signed in but nothing learned yet: show the welcome hero instead of empty cards. */
    val isFirstRun: Boolean get() = signedIn && !loading && attempts.isEmpty() && !hasActivity
}

/** Pure assembly of [HomeUiState] from what the repositories emit; tested without Android. */
internal fun buildHomeState(
    profile: UserProfile?,
    lessons: List<GrammarLesson>,
    attempts: List<AttemptSummary>,
    stats: List<DailyStudyStatEntity>,
    activities: List<LearningActivityEntity>,
    dueCount: Int?,
    today: LocalDate,
): HomeUiState {
    // Streak and XP use the same rules as the Tiến độ screen.
    val progress = calculateProgress(stats, 0, emptyList(), today)
    val todaySeconds = stats.firstOrNull { it.studyDate == today.toString() }?.studySeconds ?: 0
    val lastLesson = activities.filter { it.activityType == ActivityType.GRAMMAR }
        .sortedByDescending { it.occurredAt }
        .firstNotNullOfOrNull { it.sourceId?.toLongOrNull() }
    return HomeUiState(profile = profile, lessons = lessons, attempts = attempts, signedIn = true,
        todayMinutes = (todaySeconds / 60).toInt(), streak = progress.streak, totalXp = progress.xp, dueCount = dueCount,
        lastLessonId = lastLesson, hasActivity = activities.isNotEmpty() || stats.isNotEmpty())
}

/** Moves [word] to the front of the recent list without duplicates, keeping at most 8. */
internal fun recentAfterOpen(recent: List<String>, word: String): List<String> =
    (listOf(word) + recent.filterNot { it.equals(word, ignoreCase = true) }).take(8)

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
    study: StudyDao,
    reviews: ReviewRepository,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = session.user.flatMapLatest { user ->
        if (user == null) grammar.observeLessons().map { HomeUiState(lessons = it) }
        else {
            val uid = user.uid
            val core = combine(profiles.observe(uid), attempts.observeHistory(uid), grammar.observeLessons()) { p, a, l -> Triple(p, a, l) }
            // Progress inputs are optional: a failure hides their blocks instead of failing the whole screen.
            val stats = study.observeAllStats(uid).catch { emit(emptyList()) }
            val activities = study.observeActivities(uid, "0000-01-01", "9999-12-31").catch { emit(emptyList()) }
            val due = reviews.observeDueCount(uid).map<Int, Int?> { it }.catch { emit(null) }
            combine(core, stats, activities, due) { (p, a, l), s, act, d ->
                val today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
                buildHomeState(p, l, a, s, act, d, today)
            }
        }
    }.catch { emit(HomeUiState(failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(loading = true))

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
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    /** Words opened from search in this session, newest first; kept in memory only. */
    val recentSearches: StateFlow<List<String>> = _recent.asStateFlow()

    fun open(word: Word, onReady: (Word) -> Unit) {
        _recent.update { recentAfterOpen(it, word.word) }
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
