package com.engmate.app.feature.quiz.domain

/** What a quiz is built from. Encoded as a short string so a screen can keep it in rememberSaveable. */
sealed interface QuizSource {
    data class Lesson(val lessonId: Long) : QuizSource
    data class Topic(val topicId: Long) : QuizSource
    data object Placement : QuizSource

    fun encode(): String = when (this) {
        is Lesson -> "$LESSON$lessonId"
        is Topic -> "$TOPIC$topicId"
        Placement -> PLACEMENT
    }

    companion object {
        private const val LESSON = "lesson:"
        private const val TOPIC = "topic:"
        private const val PLACEMENT = "placement"

        /** Returns null for anything [encode] cannot produce. */
        fun decode(value: String?): QuizSource? = when {
            value == null -> null
            value == PLACEMENT -> Placement
            value.startsWith(LESSON) -> value.removePrefix(LESSON).toLongOrNull()?.let(::Lesson)
            value.startsWith(TOPIC) -> value.removePrefix(TOPIC).toLongOrNull()?.let(::Topic)
            else -> null
        }
    }
}
