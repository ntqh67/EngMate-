package com.engmate.app.feature.quiz.presentation.placement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.Spacing
import com.engmate.app.core.ui.theme.screenPadding
import com.engmate.app.feature.quiz.domain.QuizScoring
import com.engmate.app.feature.quiz.domain.QuizSource
import com.engmate.app.feature.quiz.presentation.quiz.QuizContent
import com.engmate.app.feature.quiz.presentation.quiz.QuizViewModel
import com.engmate.app.feature.quiz.presentation.result.QuizResultContent
import com.engmate.app.feature.quiz.presentation.result.QuizResultViewModel

private enum class Step { INTRO, QUIZ, RESULT, REVIEW }

/** One sentence per estimated level, shown under the level badge. */
internal fun levelExplanation(level: String): String = when (level) {
    "A1" -> "Bạn nắm được các cấu trúc cơ bản nhất. Hãy bắt đầu với các thì hiện tại và động từ to be."
    "A2" -> "Bạn dùng được câu đơn giản về đời sống hằng ngày. Tiếp theo là các thì quá khứ và so sánh."
    "B1" -> "Bạn xử lý được hầu hết tình huống quen thuộc. Hãy luyện thêm câu điều kiện và thì hoàn thành."
    else -> "Bạn dùng ngữ pháp khá vững. Hãy ôn các cấu trúc nâng cao để giữ phong độ."
}

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
                PrimaryButton("Xong", { onFinished(level) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit, onLater: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { AppTopBar("Kiểm tra trình độ", onBack) },
        bottomBar = {
            StickyBottomBar {
                PrimaryButton("Bắt đầu", onStart, Modifier.fillMaxWidth())
                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) { Text("Để sau") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            LevelsIllustration()
            Text("Tìm điểm xuất phát của bạn", style = MaterialTheme.typography.headlineMedium)
            Text("Trả lời nhanh, đừng đoán quá lâu. EngMate dùng kết quả để gợi ý bài vừa sức và cập nhật trình độ trong hồ sơ.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SurfaceGroup {
                Fact(R.drawable.ic_list, "${QuizScoring.PLACEMENT_QUESTION_COUNT} câu hỏi", "Trắc nghiệm và điền từ")
                GroupDivider(56.dp)
                Fact(R.drawable.ic_clock, "Khoảng 10 phút", "Làm một lần, không giới hạn thời gian")
                GroupDivider(56.dp)
                Fact(R.drawable.ic_target, "Gợi ý trình độ A1–B2", "Không trừ điểm khi trả lời sai")
            }
        }
    }
}

/** Brand block with the level badges around the EngMate mark (Figma F04). */
@Composable
private fun LevelsIllustration() {
    val brand = EngMateTheme.brand
    Box(Modifier.fillMaxWidth().height(168.dp).background(brand.brand, MaterialTheme.shapes.large).semantics { contentDescription = "Các trình độ từ A1 đến B2" }) {
        Box(Modifier.align(Alignment.Center).size(72.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large), Alignment.Center) {
            Text("E", style = EngMateTheme.type.stat, color = MaterialTheme.colorScheme.onPrimary)
        }
        LevelChip("A1", Modifier.align(Alignment.TopStart).padding(Spacing.xl), large = true)
        LevelChip("A2", Modifier.align(Alignment.TopEnd).padding(Spacing.xl), large = true)
        LevelChip("B1", Modifier.align(Alignment.BottomStart).padding(Spacing.xl), large = true)
        LevelChip("B2", Modifier.align(Alignment.BottomEnd).padding(Spacing.xl), large = true)
    }
}

@Composable
private fun Fact(icon: Int, title: String, body: String) {
    ListRow(title, supporting = body, leading = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) })
}

@Composable
private fun LevelResult(level: String, correct: Int, total: Int, levelSaved: Boolean, onDetails: () -> Unit, onDone: () -> Unit) {
    val brand = EngMateTheme.brand
    Scaffold(
        topBar = { AppTopBar("Kết quả kiểm tra", onDone, navigationIcon = R.drawable.ic_close, navigationLabel = "Đóng") },
        bottomBar = { StickyBottomBar { ButtonPair("Xem chi tiết", onDetails, "Xong", onDone) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = screenPadding, vertical = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Box(Modifier.size(148.dp).background(brand.brand, CircleShape).semantics { contentDescription = "Trình độ $level" }, Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(level, style = MaterialTheme.typography.displayMedium, color = brand.onBrand)
                    Text("Trình độ", style = MaterialTheme.typography.labelMedium, color = brand.onBrand)
                }
            }
            Text("Bạn đúng $correct/$total câu", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(levelExplanation(level), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (levelSaved) FeedbackBanner("Đã cập nhật trình độ $level vào hồ sơ", kind = FeedbackKind.SUCCESS)
            else FeedbackBanner("Chưa cập nhật được hồ sơ. Bạn có thể sửa trình độ trong Hồ sơ.", kind = FeedbackKind.ERROR)
        }
    }
}
