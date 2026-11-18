package com.engmate.app.feature.quiz.presentation.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engmate.app.R
import com.engmate.app.core.ui.components.BackTopBar
import com.engmate.app.core.ui.components.EmptyState
import com.engmate.app.core.ui.theme.onSuccessContainer
import com.engmate.app.core.ui.theme.success
import com.engmate.app.core.ui.theme.successContainer
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

    Scaffold(topBar = { BackTopBar(state.title.ifEmpty { "Làm bài" }, ::leave) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val question = state.current
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.loadError != null -> EmptyState(state.loadError!!, action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = ::leave) { Text("Quay lại") }
                        Button(onClick = viewModel::retryLoad) { Text("Thử lại") }
                    }
                })
                state.emptyMessage != null -> EmptyState(state.emptyMessage!!, action = {
                    OutlinedButton(onClick = ::leave) { Text("Quay lại") }
                })
                question != null -> QuestionStep(state, question, viewModel, onOpenProfile)
            }
        }
    }

    if (state.confirmExit) AlertDialog(
        onDismissRequest = viewModel::dismissExit,
        title = { Text("Thoát bài làm?") },
        text = { Text("Kết quả sẽ không được lưu.") },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmExit(); onBack() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Thoát") }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissExit) { Text("Làm tiếp") } },
    )
}

@Composable
private fun QuestionStep(state: QuizUiState, question: Question, viewModel: QuizViewModel, onOpenProfile: () -> Unit) {
    val answer = state.currentAnswer
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
            .widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Câu ${state.index + 1}/${state.questions.size}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { (state.index + 1f) / state.questions.size }, modifier = Modifier.fillMaxWidth())
            }
            Text(question.content, style = MaterialTheme.typography.titleLarge)
            when (question.answerType) {
                AnswerType.MULTIPLE_CHOICE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    question.options.forEach { option -> OptionCard(option, answer, enabled = !state.saving) { viewModel.select(option.id) } }
                }
                AnswerType.FILL_BLANK -> OutlinedTextField(
                    value = answer.text, onValueChange = viewModel::type, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Câu trả lời của bạn") }, singleLine = true, enabled = !answer.checked && !state.saving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.check() }),
                )
            }
            if (answer.checked) Feedback(question, answer)
            state.saveError?.let { SaveErrorCard(it, viewModel::retrySave, onOpenProfile) }
        }
        Surface(tonalElevation = 2.dp) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                when {
                    state.saving -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Đang lưu kết quả…")
                    }
                    !answer.checked -> Button(onClick = viewModel::check, enabled = state.canCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("Kiểm tra")
                    }
                    state.saveError == null -> Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.isLast) "Xem kết quả" else "Câu tiếp theo")
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(option: QuestionOption, answer: QuizAnswer, enabled: Boolean, onSelect: () -> Unit) {
    val selected = answer.selectedOptionId == option.id
    val colors = MaterialTheme.colorScheme
    // After checking: the correct option turns green, a wrong pick turns red; both carry an icon, not just color.
    val mark: Pair<Int, Color>? = when {
        !answer.checked -> null
        option.isCorrect -> R.drawable.ic_check_circle to colors.success
        selected -> R.drawable.ic_cancel to colors.error
        else -> null
    }
    val container = when {
        answer.checked && option.isCorrect -> colors.successContainer
        answer.checked && selected -> colors.errorContainer
        selected -> colors.primaryContainer
        else -> colors.surfaceContainerLow
    }
    val content = when {
        answer.checked && option.isCorrect -> colors.onSuccessContainer
        answer.checked && selected -> colors.onErrorContainer
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        shape = MaterialTheme.shapes.medium, color = container, contentColor = content,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected && !answer.checked) colors.primary else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled && !answer.checked,
            role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled && !answer.checked)
            Spacer(Modifier.width(8.dp))
            Text(option.content, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            mark?.let { (icon, tint) -> Icon(painterResource(icon), null, tint = tint) }
        }
    }
}

@Composable
private fun Feedback(question: Question, answer: QuizAnswer) {
    val colors = MaterialTheme.colorScheme
    val (container, content, icon, title) = if (answer.correct)
        Quad(colors.successContainer, colors.onSuccessContainer, R.drawable.ic_check_circle, "Chính xác!")
    else Quad(colors.errorContainer, colors.onErrorContainer, R.drawable.ic_cancel, "Chưa đúng")
    Surface(shape = MaterialTheme.shapes.medium, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Đáp án đúng: " + question.correctOptions.joinToString(" / ") { it.content })
            question.explanation?.takeIf { it.isNotBlank() }?.let { Text("Giải thích: $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private data class Quad(val container: Color, val content: Color, val icon: Int, val title: String)

@Composable
private fun SaveErrorCard(error: QuizSaveError, onRetry: () -> Unit, onOpenProfile: () -> Unit) {
    val message = when (error) {
        QuizSaveError.SignInRequired -> "Bạn cần đăng nhập để lưu kết quả."
        QuizSaveError.ProfileRequired -> "Hãy tạo hồ sơ cá nhân trước khi lưu kết quả."
        is QuizSaveError.Invalid -> error.message
        QuizSaveError.Failed -> "Không lưu được kết quả. Câu trả lời vẫn được giữ, hãy thử lại."
    }
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error == QuizSaveError.ProfileRequired) Button(onClick = onOpenProfile) { Text("Tạo hồ sơ") }
                OutlinedButton(onClick = onRetry) { Text("Thử lại") }
            }
        }
    }
}
