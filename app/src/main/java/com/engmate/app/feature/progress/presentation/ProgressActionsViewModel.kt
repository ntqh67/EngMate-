package com.engmate.app.feature.progress.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.core.contracts.ProgressRecorder
import com.engmate.app.feature.auth.domain.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Lets screens report learning actions; nothing is recorded for guests, and a recording failure never reaches the UI. */
@HiltViewModel
class ProgressActionsViewModel @Inject constructor(
    private val recorder: ProgressRecorder, private val session: UserSession, private val clock: Clock,
) : ViewModel() {
    fun lessonOpened(lessonId: Long) = record { recorder.onLessonStudied(it, lessonId, clock.millis()) }
    fun pronunciationScored(score: Int) = record { recorder.onPronunciationPracticed(it, score, clock.millis()) }

    private fun record(block: suspend (uid: String) -> Unit) {
        val uid = session.user.value?.uid ?: return
        viewModelScope.launch { runCatching { block(uid) } }
    }
}
