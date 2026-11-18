package com.engmate.app.feature.quiz.presentation.placement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.R
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.LevelBadge
import com.engmate.app.core.ui.theme.successText
import com.engmate.app.feature.quiz.domain.QuizScoring
import com.engmate.app.feature.quiz.domain.QuizSource
import com.engmate.app.feature.quiz.presentation.quiz.QuizContent
import com.engmate.app.feature.quiz.presentation.quiz.QuizViewModel
import com.engmate.app.feature.quiz.presentation.result.QuizResultContent
import com.engmate.app.feature.quiz.presentation.result.QuizResultViewModel

private enum class Step { INTRO, QUIZ, RESULT, REVIEW }

/**
 * Placement test: intro, the 15 questions, then the estimated level.
 * [onFinished] gets the level (A1–B2), or null when the learner chooses "Để sau". Onboarding calls this screen.
 * [onOpenProfile] is offered when the result cannot be saved because there is no profile yet.
 */
@Composable
fun PlacementScreen(onFinished: (level: String?) -> Unit, onBack: () -> Unit, onOpenProfile: () -> Unit = {}) {
    var step by rememberSaveable { mutableStateOf(Step.INTRO) }
    // The saved result, kept as plain values so it survives rotation.
    var attemptId by rememberSaveable { mutableLongStateOf(0L) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var total by rememberSaveable { mutableIntStateOf(0) }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var levelSaved by rememberSaveable { mutableStateOf(false) }

    when (step) {
        Step.INTRO -> Intro(onStart = { step = Step.QUIZ }, onLater = { onFinished(null) }, onBack = onBack)
        Step.QUIZ -> {
            val quiz: QuizViewModel = viewModel(key = "quiz:${QuizSource.Placement.encode()}")
            LaunchedEffect(Unit) { quiz.start(QuizSource.Placement) }
            QuizContent(quiz, onOpenProfile = onOpenProfile, onBack = { step = Step.INTRO }, onSaved = { r ->
                attemptId = r.attemptId; correct = r.correctCount; total = r.totalQuestions
                level = r.level ?: QuizScoring.levelFromScore(r.correctCount.coerceIn(0, QuizScoring.PLACEMENT_QUESTION_COUNT))
                levelSaved = r.levelSaved
                step = Step.RESULT
            })
        }
        Step.RESULT -> {
            // The attempt is already stored, so leaving this step means finishing.
            BackHandler { onFinished(level) }
            LevelResult(level.orEmpty(), correct, total, levelSaved, onDetails = { step = Step.REVIEW }, onDone = { onFinished(level) })
        }
        Step.REVIEW -> {
            BackHandler { step = Step.RESULT }
            val review: QuizResultViewModel = viewModel(key = "quiz-result:$attemptId")
            LaunchedEffect(attemptId) { review.load(attemptId) }
            val state by review.state.collectAsStateWithLifecycle()
            QuizResultContent(state, title = "Chi tiết bài kiểm tra", onBack = { step = Step.RESULT },
                onFilter = review::filter, onReload = review::retry) {
                Button(onClick = { onFinished(level) }, modifier = Modifier.fillMaxWidth()) { Text("Xong") }
            }
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit, onLater: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar("Kiểm tra trình độ", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(R.drawable.ic_school), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("Xác định trình độ tiếng Anh của bạn", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Fact("${QuizScoring.PLACEMENT_QUESTION_COUNT} câu hỏi trắc nghiệm và điền từ")
                    Fact("Khoảng 10 phút")
                    Fact("Gợi ý trình độ từ A1 đến B2")
                    Fact("Không trừ điểm khi trả lời sai")
                }
            }
            Text("Kết quả được lưu vào lịch sử và cập nhật trình độ trong hồ sơ của bạn.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Bắt đầu") }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("Để sau") }
        }
    }
}

@Composable
private fun Fact(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_check_circle), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LevelResult(level: String, correct: Int, total: Int, levelSaved: Boolean, onDetails: () -> Unit, onDone: () -> Unit) {
    Scaffold(topBar = { BackTopBar("Kết quả kiểm tra", onDone) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Trình độ của bạn", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(level, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            LevelBadge("$correct/$total câu đúng")
            val (icon, color, message) = if (levelSaved)
                Triple(R.drawable.ic_check_circle, MaterialTheme.colorScheme.successText, "Đã cập nhật trình độ vào hồ sơ")
            else Triple(R.drawable.ic_cancel, MaterialTheme.colorScheme.error, "Chưa cập nhật được hồ sơ, bạn có thể sửa trình độ trong Hồ sơ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(message, color = color, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Xong") }
            OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Xem chi tiết") }
        }
    }
}
