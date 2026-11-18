package com.engmate.app.feature.grammar.presentation.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.grammar.domain.GrammarLesson
import com.engmate.app.feature.grammar.domain.GrammarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*

data class GrammarLessonsUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val lessons: List<GrammarLesson> = emptyList(),
    val level: String? = null,
) {
    val levels: List<String> get() = lessons.map { it.level }.distinct().sorted()
    val visible: List<GrammarLesson> get() = lessons.filter { level == null || it.level == level }.sortedBy { it.orderIndex }
    fun lesson(id: Long) = lessons.firstOrNull { it.id == id }
}

/** Read-only lesson browsing for learners; editing lives in GrammarAdminViewModel. */
@HiltViewModel
class GrammarLessonsViewModel @Inject constructor(repository: GrammarRepository) : ViewModel() {
    private val level = MutableStateFlow<String?>(null)

    val state: StateFlow<GrammarLessonsUiState> = combine(repository.observeLessons(), level) { lessons, level ->
        GrammarLessonsUiState(loading = false, lessons = lessons, level = level)
    }.catch { emit(GrammarLessonsUiState(loading = false, failed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarLessonsUiState())

    fun filter(level: String?) { this.level.value = level }
}
