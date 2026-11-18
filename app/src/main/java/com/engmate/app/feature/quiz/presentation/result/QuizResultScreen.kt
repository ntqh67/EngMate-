package com.engmate.app.feature.quiz.presentation.result

import androidx.compose.runtime.Composable
import com.engmate.app.core.ui.components.UnderConstructionScreen
import com.engmate.app.feature.quiz.domain.QuizSource

/**
 * Shows the result of a saved attempt. [onRetry] starts a new quiz from the same source.
 * The signature is the public entry point; the body is a placeholder until the quiz flow ships.
 */
@Composable
fun QuizResultScreen(attemptId: Long, onRetry: (QuizSource) -> Unit, onBack: () -> Unit) {
    UnderConstructionScreen("Kết quả", onBack, listOf("Mã lượt làm: $attemptId"))
}
