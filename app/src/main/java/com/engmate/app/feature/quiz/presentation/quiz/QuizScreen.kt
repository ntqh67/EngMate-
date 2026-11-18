package com.engmate.app.feature.quiz.presentation.quiz

import androidx.compose.runtime.Composable
import com.engmate.app.core.ui.components.UnderConstructionScreen
import com.engmate.app.feature.quiz.domain.QuizSource

/**
 * Takes a quiz built from [source]. Calls [onFinished] with the saved attempt id.
 * The signature is the public entry point; the body is a placeholder until the quiz flow ships.
 */
@Composable
fun QuizScreen(source: QuizSource, onFinished: (attemptId: Long) -> Unit, onBack: () -> Unit) {
    UnderConstructionScreen("Làm bài", onBack, listOf("Nguồn câu hỏi: ${source.encode()}"))
}
