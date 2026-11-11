package com.engmate.app.feature.grammar.domain

data class GrammarExample(val en: String, val vi: String)

data class GrammarLesson(
    val id: Long,
    val title: String,
    val level: String,
    val summary: String?,
    val content: String,
    val examples: List<GrammarExample>,
    val orderIndex: Int,
    val updatedAt: Long?,
)

data class LessonInput(
    val title: String = "",
    val level: String = "A1",
    val summary: String = "",
    val content: String = "",
    val examples: List<GrammarExample> = emptyList(),
) {
    fun normalized() = copy(
        title = title.trim(), level = level.trim(), summary = summary.trim(), content = content.trim(),
        examples = examples.map { GrammarExample(it.en.trim(), it.vi.trim()) }
            .filterNot { it.en.isEmpty() && it.vi.isEmpty() },
    )
}

enum class LessonField { TITLE, LEVEL, CONTENT, EXAMPLES }

object LessonValidator {
    val levels = listOf("A1", "A2", "B1", "B2")
    fun errors(input: LessonInput): Map<LessonField, String> = buildMap {
        val value = input.normalized()
        if (value.title.length !in 2..100) put(LessonField.TITLE, "Tiêu đề từ 2–100 ký tự.")
        if (value.level !in levels) put(LessonField.LEVEL, "Chọn trình độ A1, A2, B1 hoặc B2.")
        if (value.content.isEmpty()) put(LessonField.CONTENT, "Nội dung bài học không được để trống.")
        if (value.examples.any { it.en.isEmpty() || it.vi.isEmpty() })
            put(LessonField.EXAMPLES, "Mỗi ví dụ cần cả câu tiếng Anh và nghĩa tiếng Việt.")
    }
}

class InvalidLesson(val errors: Map<LessonField, String>) : IllegalArgumentException("Bài ngữ pháp không hợp lệ")
class LessonMissing : IllegalStateException("Bài ngữ pháp không còn tồn tại")
class LessonInUse : IllegalStateException("Bài đã có người làm bài tập, không thể xoá")
