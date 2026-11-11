package com.engmate.app.feature.quiz.domain

enum class AnswerType { MULTIPLE_CHOICE, FILL_BLANK }

/** Each question belongs to exactly one category. */
enum class QuestionCategory(val label: String) {
    VOCABULARY("Từ vựng"), GRAMMAR("Ngữ pháp"), PLACEMENT("Kiểm tra trình độ");

    companion object {
        fun of(topicId: Long?, lessonId: Long?, isPlacement: Boolean): QuestionCategory? = when {
            isPlacement && topicId == null && lessonId == null -> PLACEMENT
            topicId != null && lessonId == null && !isPlacement -> VOCABULARY
            lessonId != null && topicId == null && !isPlacement -> GRAMMAR
            else -> null
        }
    }
}

data class QuestionOption(val id: Long, val content: String, val isCorrect: Boolean, val orderIndex: Int)

data class Question(
    val id: Long,
    val topicId: Long?,
    val lessonId: Long?,
    val isPlacement: Boolean,
    val answerType: AnswerType,
    val content: String,
    val explanation: String?,
    val options: List<QuestionOption>,
    val createdAt: Long?,
) {
    val category: QuestionCategory? get() = QuestionCategory.of(topicId, lessonId, isPlacement)
    val correctOptions: List<QuestionOption> get() = options.filter { it.isCorrect }
}

data class OptionInput(val content: String, val isCorrect: Boolean)

data class QuestionInput(
    val topicId: Long? = null,
    val lessonId: Long? = null,
    val isPlacement: Boolean = false,
    val answerType: AnswerType = AnswerType.MULTIPLE_CHOICE,
    val content: String = "",
    val explanation: String = "",
    val options: List<OptionInput> = emptyList(),
) {
    fun normalized() = copy(
        content = content.trim(), explanation = explanation.trim(),
        options = options.map { it.copy(content = QuizScoring.collapseSpaces(it.content)) },
    )
}

enum class QuestionField { CATEGORY, CONTENT, OPTIONS, CORRECT }

object QuestionValidator {
    const val MULTIPLE_CHOICE_OPTIONS = 4

    fun errors(input: QuestionInput): Map<QuestionField, String> = buildMap {
        val value = input.normalized()
        if (QuestionCategory.of(value.topicId, value.lessonId, value.isPlacement) == null)
            put(QuestionField.CATEGORY, "Câu hỏi phải thuộc đúng một loại: chủ đề từ vựng, bài ngữ pháp hoặc kiểm tra trình độ.")
        if (value.content.isEmpty()) put(QuestionField.CONTENT, "Nội dung câu hỏi không được để trống.")
        val options = value.options
        val duplicated = options.map { QuizScoring.normalize(it.content) }.filter { it.isNotEmpty() }
            .let { it.size != it.toSet().size }
        when (value.answerType) {
            AnswerType.MULTIPLE_CHOICE -> {
                when {
                    options.size != MULTIPLE_CHOICE_OPTIONS -> put(QuestionField.OPTIONS, "Câu trắc nghiệm cần đúng 4 đáp án.")
                    options.any { it.content.isEmpty() } -> put(QuestionField.OPTIONS, "Đáp án không được để trống.")
                    duplicated -> put(QuestionField.OPTIONS, "Các đáp án không được trùng nhau.")
                }
                if (options.count { it.isCorrect } != 1) put(QuestionField.CORRECT, "Câu trắc nghiệm cần đúng 1 đáp án đúng.")
            }
            AnswerType.FILL_BLANK -> when {
                options.isEmpty() -> put(QuestionField.OPTIONS, "Câu điền từ cần ít nhất 1 đáp án chấp nhận.")
                options.any { it.content.isEmpty() } -> put(QuestionField.OPTIONS, "Đáp án không được để trống.")
                duplicated -> put(QuestionField.OPTIONS, "Các đáp án không được trùng nhau.")
                options.any { !it.isCorrect } -> put(QuestionField.CORRECT, "Mọi đáp án của câu điền từ phải là đáp án đúng.")
            }
        }
    }
}

class InvalidQuestion(val errors: Map<QuestionField, String>) : IllegalArgumentException("Câu hỏi không hợp lệ")
class QuestionMissing : IllegalStateException("Câu hỏi không còn tồn tại")
class QuestionInUse : IllegalStateException("Câu hỏi đã có trong lịch sử làm bài, không thể sửa hoặc xoá")
