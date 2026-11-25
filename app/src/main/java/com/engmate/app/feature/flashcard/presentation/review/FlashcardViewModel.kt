package com.engmate.app.feature.flashcard.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.flashcard.domain.ReviewRepository
import com.engmate.app.feature.flashcard.domain.Sm2
import com.engmate.app.feature.notebook.domain.HandbookRepository
import com.engmate.app.feature.notebook.domain.WordNote
import com.engmate.app.feature.notebook.domain.WordNoteRepository
import com.engmate.app.feature.vocabulary.domain.Word
import com.engmate.app.feature.vocabulary.domain.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** One study round over a handbook. [known] holds this round's answers: true = Đã thuộc. */
data class Session(
    val handbookId: Long = 0,
    val title: String = "",
    val cards: List<String> = emptyList(),
    val index: Int = 0,
    val flipped: Boolean = false,
    val known: Map<String, Boolean> = emptyMap(),
    val finished: Boolean = false,
    val loading: Boolean = true,
) {
    val current: String? get() = cards.getOrNull(index)
    val knownCount get() = known.count { it.value }
    val unknown get() = cards.filter { known[it] != true }
}

data class FlashcardState(val session: Session = Session(), val word: Word? = null, val note: WordNote? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FlashcardViewModel @Inject constructor(
    private val session: UserSession,
    private val handbooks: HandbookRepository,
    private val words: WordRepository,
    private val notes: WordNoteRepository,
    private val reviews: ReviewRepository,
) : ViewModel() {
    private val round = MutableStateFlow(Session())

    // Word data is re-read only when the card changes, so flipping and answering stay instant.
    private val cardData: Flow<Pair<Word?, WordNote?>> = round.map { it.current }.distinctUntilChanged().flatMapLatest { word ->
        val uid = session.user.value?.uid
        if (word == null || uid == null) flowOf(null to null)
        else combine(words.observe(word), notes.observe(uid, word)) { w, n -> w to n }
    }.catch { emit(null to null) }

    val state: StateFlow<FlashcardState> = combine(round, cardData) { s, (w, n) ->
        // While the next card's data loads, never show the previous word on it.
        if (w != null && w.word != s.current) FlashcardState(s) else FlashcardState(s, w, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlashcardState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    /** Starts a round over the handbook; [only] limits it to some words ("Học lại thẻ chưa thuộc"). */
    fun start(handbookId: Long, only: List<String>? = null) {
        val uid = session.user.value?.uid ?: return
        if (handbookId == DUE && only == null) { startDue(); return }
        if (only == null && round.value.handbookId == handbookId && !round.value.loading) return // keep progress on recomposition
        round.value = Session(handbookId, loading = true)
        viewModelScope.launch {
            val title = if (handbookId == DUE) DUE_TITLE else handbooks.observeHandbooks(uid).first().firstOrNull { it.id == handbookId }?.title.orEmpty()
            val cards = only ?: handbooks.observeEntries(uid, handbookId).first().map { it.word }
            round.value = Session(handbookId, title, cards, loading = false, finished = cards.isEmpty())
        }
    }

    /** Ôn tập hôm nay: a round over every word whose SM-2 review is due, across all handbooks. Scoring is unchanged. */
    fun startDue() {
        val uid = session.user.value?.uid ?: return
        // Keep an unfinished round on recomposition; a finished one starts over with what is due now.
        if (round.value.handbookId == DUE && !round.value.loading && !round.value.finished) return
        round.value = Session(DUE, loading = true)
        viewModelScope.launch {
            val cards = try { reviews.dueWords(uid) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            round.value = Session(DUE, DUE_TITLE, cards, loading = false, finished = cards.isEmpty())
        }
    }

    fun flip() = round.update { it.copy(flipped = !it.flipped) }
    fun previous() = round.update { if (it.index > 0) it.copy(index = it.index - 1, flipped = false) else it }
    fun next() = round.update { if (it.index + 1 >= it.cards.size) it.copy(finished = true) else it.copy(index = it.index + 1, flipped = false) }
    fun shuffle() = round.update { it.copy(cards = it.cards.shuffled(), index = 0, flipped = false) }

    /** Records the answer in SM-2 ("Đã thuộc" → [Sm2.KNOWN]) and moves on. The round continues even if saving fails. */
    fun mark(known: Boolean) {
        val s = round.value
        val word = s.current ?: return
        val uid = session.user.value?.uid ?: return
        round.value = s.copy(known = s.known + (word to known))
        next()
        viewModelScope.launch {
            try { reviews.review(uid, word, if (known) Sm2.KNOWN else Sm2.NOT_KNOWN) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _message.value = "Không lưu được tiến độ của “$word”." }
        }
    }

    fun restart(onlyUnknown: Boolean) {
        val s = round.value
        start(s.handbookId, if (onlyUnknown) s.unknown else s.cards)
    }

    companion object {
        /** Session id of the due-words round; real handbook ids are positive. */
        const val DUE = -1L
        const val DUE_TITLE = "Ôn tập hôm nay"
    }
}
