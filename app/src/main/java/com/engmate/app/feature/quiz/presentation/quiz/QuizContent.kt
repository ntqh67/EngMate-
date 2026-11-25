package com.engmate.app.feature.quiz.presentation.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.*
import com.engmate.app.core.ui.theme.*
import com.engmate.app.feature.quiz.domain.*

/**
 * Question-by-question quiz body shared by QuizScreen and PlacementScreen.
 * [onSaved] runs once the attempt is stored; [onBack] runs after any exit confirmation.
 */
@Composable
internal fun QuizContent(viewModel: QuizViewModel, onSaved: (AttemptResult) -> Unit, onBack: () -> Unit, onOpenProfile: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val result = state.result
    LaunchedEffect(result) { if (result != null) onSaved(result) }

    fun leave() { if (viewModel.requestExit()) onBack() }
    BackHandler { leave() }

    val question = state.current
    Scaffold(
        topBar = { QuizTopBar(state, ::leave) },
        bottomBar = { if (question != null) QuizBottomBar(state, question, viewModel, onOpenProfile) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState(Modifier.padding(screenPadding), rows = 4, hero = false)
                state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::retryLoad)
                state.emptyMessage != null -> EmptyState(state.emptyMessage!!, icon = R.drawable.ic_assignment,
                    action = { SecondaryButton("Quay lại", ::leave) })
                question != null -> QuestionStep(state, question, viewModel)
            }
        }
    }

    if (state.confirmExit) ConfirmDialog("Thoát bài làm?", "Kết quả sẽ không được lưu.", "Thoát",
        onConfirm = { viewModel.confirmExit(); onBack() }, onDismiss = viewModel::dismissExit)
}

/** Close, one segment per question, "x/y" and the XP earned so far. */
@Composable
private fun QuizTopBar(state: QuizUiState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(end = screenPadding, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), "Thoát bài làm") }
        if (state.questions.isNotEmpty()) {
            val done = state.answers.count { it.checked }
            SegmentedProgress(done, state.questions.size, Modifier.weight(1f))
            Text("${state.index + 1}/${state.questions.size}", style = MaterialTheme.typography.labelLarge)
            val correct = state.answers.count { it.checked && it.correct }
            XpChip(QuizScoring.xp(correct).toLong())
        } else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun QuestionStep(state: QuizUiState, question: Question, viewModel: QuizViewModel) {
    val answer = state.currentAnswer
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Choices sit toward the thumb; a typed answer stays under the question because the keyboard takes the bottom.
        val choices = question.answerType == AnswerType.MULTIPLE_CHOICE
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
            .padding(horizontal = screenPadding, vertical = Spacing.l), verticalArrangement = if (choices) Arrangement.SpaceBetween else Arrangement.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (state.title.isNotBlank()) Text(state.title, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (question.answerType == AnswerType.MULTIPLE_CHOICE) "Chọn đáp án đúng" else "Điền vào chỗ trống",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(question.content, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(Spacing.xl))
            when (question.answerType) {
                AnswerType.MULTIPLE_CHOICE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    question.options.forEachIndexed { i, option -> OptionCard(i, option, answer, enabled = !state.saving) { viewModel.select(option.id) } }
                }
                AnswerType.FILL_BLANK -> OutlinedTextField(
                    value = answer.text, onValueChange = viewModel::type, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Câu trả lời của bạn") }, singleLine = true, enabled = !answer.checked && !state.saving,
                    textStyle = MaterialTheme.typography.titleLarge, shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.check() }),
                    trailingIcon = if (!answer.checked) null else {
                        { Icon(painterResource(if (answer.correct) R.drawable.ic_check_circle else R.drawable.ic_cancel), null,
                            tint = if (answer.correct) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error) }
                    },
                )
            }
        }
    }
}

@Composable
private fun OptionCard(index: Int, option: QuestionOption, answer: QuizAnswer, enabled: Boolean, onSelect: () -> Unit) {
    val selected = answer.selectedOptionId == option.id
    val c = MaterialTheme.colorScheme
    // After checking: the correct option turns green, a wrong pick turns red; both carry an icon, not just color.
    val mark: Pair<Int, Color>? = when {
        !answer.checked -> null
        option.isCorrect -> R.drawable.ic_check_circle to c.success
        selected -> R.drawable.ic_cancel to c.error
        else -> null
    }
    val container = when {
        answer.checked && option.isCorrect -> c.successContainer
        answer.checked && selected -> c.errorContainer
        selected -> c.primaryContainer
        else -> c.surfaceContainerLow
    }
    val content = when {
        answer.checked && option.isCorrect -> c.onSuccessContainer
        answer.checked && selected -> c.onErrorContainer
        selected -> c.onPrimaryContainer
        else -> c.onSurface
    }
    val border = when {
        selected && !answer.checked -> BorderStroke(2.dp, c.primary)
        answer.checked && option.isCorrect -> BorderStroke(2.dp, c.success)
        answer.checked && selected -> BorderStroke(2.dp, c.error)
        else -> BorderStroke(1.dp, c.outlineVariant)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = container, contentColor = content, border = border,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled && !answer.checked, role = Role.RadioButton, onClick = onSelect)) {
        Row(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(32.dp).background(if (selected) c.primary else c.surfaceContainerHigh, CircleShape), Alignment.Center) {
                Text(('A' + index).toString(), style = MaterialTheme.typography.labelLarge, color = if (selected) c.onPrimary else c.onSurfaceVariant)
            }
            Text(option.content, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            mark?.let { (icon, tint) -> Icon(painterResource(icon), null, tint = tint) }
        }
    }
}

/** Check, then feedback on the same bar (green or red) so the question never jumps. */
@Composable
private fun QuizBottomBar(state: QuizUiState, question: Question, viewModel: QuizViewModel, onOpenProfile: () -> Unit) {
    val answer = state.currentAnswer
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.index, answer.checked) {
        if (answer.checked) haptics.performHapticFeedback(if (answer.correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    val (bg, fg) = when {
        !answer.checked || state.saveError != null -> c.surface to c.onSurface
        answer.correct -> c.successContainer to c.onSuccessContainer
        else -> c.errorContainer to c.onErrorContainer
    }
    StickyBottomBar(Modifier.animateContentSize(), containerColor = bg, contentColor = fg) {
        when {
            state.saving -> Row(Modifier.fillMaxWidth().heightIn(min = Spacing.button), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(Spacing.m)); Text("Đang lưu kết quả…")
            }
            state.saveError != null -> SaveError(state.saveError!!, viewModel::retrySave, onOpenProfile)
            !answer.checked -> PrimaryButton("Kiểm tra", viewModel::check, Modifier.fillMaxWidth(), enabled = state.canCheck)
            else -> {
                Feedback(question, answer, state.index)
                PrimaryButton(if (state.isLast) "Xem kết quả" else "Tiếp tục", viewModel::next, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (answer.correct) c.success else c.error,
                        contentColor = if (answer.correct) c.surface else c.onError))
            }
        }
    }
}

@Composable
private fun Feedback(question: Question, answer: QuizAnswer, index: Int) {
    var expanded by rememberSaveable(index) { mutableStateOf(false) }
    val explanation = question.explanation?.takeIf { it.isNotBlank() }
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (answer.correct) R.drawable.ic_check_circle else R.drawable.ic_cancel), null)
            Spacer(Modifier.width(Spacing.s))
            Text(if (answer.correct) "Chính xác!" else "Chưa đúng", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (answer.correct) XpGain(index)
        }
        Text("Đáp án đúng: " + question.correctOptions.joinToString(" / ") { it.content }, style = MaterialTheme.typography.bodyMedium)
        if (explanation != null) Text(explanation, style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClickLabel = if (expanded) "Thu gọn giải thích" else "Xem đầy đủ giải thích") { expanded = !expanded })
    }
}

/** "+10 XP" that pops once when the answer is right; still when animations are off. */
@Composable
private fun XpGain(index: Int) {
    val reduce = rememberReduceMotion()
    val scale = remember(index) { Animatable(if (reduce) 1f else 0.6f) }
    LaunchedEffect(index) {
        if (!reduce) { scale.animateTo(1.15f, tween(150)); scale.animateTo(1f, tween(100)) }
    }
    XpChip(QuizScoring.xp(1).toLong(), Modifier.scale(scale.value), gain = true)
}

@Composable
private fun SaveError(error: QuizSaveError, onRetry: () -> Unit, onOpenProfile: () -> Unit) {
    val message = when (error) {
        QuizSaveError.SignInRequired -> "Bạn cần đăng nhập để lưu kết quả."
        QuizSaveError.ProfileRequired -> "Hãy tạo hồ sơ cá nhân trước khi lưu kết quả."
        is QuizSaveError.Invalid -> error.message
        QuizSaveError.Failed -> "Không lưu được kết quả. Câu trả lời vẫn được giữ, hãy thử lại."
    }
    FeedbackBanner(message, kind = FeedbackKind.ERROR)
    if (error == QuizSaveError.ProfileRequired) ButtonPair("Thử lại", onRetry, "Tạo hồ sơ", onOpenProfile)
    else PrimaryButton("Thử lại", onRetry, Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}
