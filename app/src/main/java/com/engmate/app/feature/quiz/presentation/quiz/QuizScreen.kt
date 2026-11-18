package com.engmate.app.feature.quiz.presentation.quiz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.feature.quiz.domain.QuizSource

/**
 * Takes a quiz built from [source] and calls [onFinished] with the saved attempt id.
 * [onOpenProfile] is offered when the result cannot be saved because the learner has no profile yet.
 */
@Composable
fun QuizScreen(source: QuizSource, onFinished: (attemptId: Long) -> Unit, onBack: () -> Unit, onOpenProfile: () -> Unit = {}) {
    // Keyed by source: navigation keeps view models at activity level, so each quiz needs its own instance.
    val viewModel: QuizViewModel = viewModel(key = "quiz:${source.encode()}")
    LaunchedEffect(source) { viewModel.start(source) }
    QuizContent(viewModel, onSaved = { onFinished(it.attemptId) }, onBack = onBack, onOpenProfile = onOpenProfile)
}
