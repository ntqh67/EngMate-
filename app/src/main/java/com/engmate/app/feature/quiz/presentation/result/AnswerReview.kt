package com.engmate.app.feature.quiz.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.engmate.app.R
import com.engmate.app.core.ui.components.ChipRow
import com.engmate.app.core.ui.components.InfoCard
import com.engmate.app.core.ui.theme.successText
import com.engmate.app.feature.quiz.domain.AnswerType
import com.engmate.app.feature.quiz.domain.AnsweredQuestion

enum class AnswerFilter(val label: String) { ALL("Tất cả"), WRONG("Câu sai"), CORRECT("Câu đúng") }

/** Filter chips with the count of each group. */
@Composable
internal fun AnswerFilterRow(total: Int, wrong: Int, selected: AnswerFilter, onSelect: (AnswerFilter) -> Unit) {
    ChipRow(AnswerFilter.entries, selected, { f ->
        "${f.label} (${when (f) { AnswerFilter.ALL -> total; AnswerFilter.WRONG -> wrong; AnswerFilter.CORRECT -> total - wrong }})"
    }, onSelect)
}

/** One reviewed answer: question, what the learner gave, accepted answers and the explanation. */
@Composable
internal fun AnswerReviewCard(number: Int, answer: AnsweredQuestion) {
    val color = if (answer.isCorrect) MaterialTheme.colorScheme.successText else MaterialTheme.colorScheme.error
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(if (answer.isCorrect) R.drawable.ic_check_circle else R.drawable.ic_cancel), null,
                    tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Câu $number · ${if (answer.isCorrect) "Đúng" else "Sai"}", color = color, style = MaterialTheme.typography.labelLarge)
            }
            Text(answer.question, style = MaterialTheme.typography.bodyLarge)
            val verb = if (answer.answerType == AnswerType.MULTIPLE_CHOICE) "Bạn đã chọn" else "Bạn đã gõ"
            Text("$verb: ${answer.given ?: "(bỏ trống)"}", color = color)
            Text("Đáp án đúng: ${answer.correctAnswers.joinToString(" / ")}")
            answer.explanation?.takeIf { it.isNotBlank() }?.let {
                Text("Giải thích: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
