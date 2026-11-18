package com.engmate.app.feature.quiz

import com.engmate.app.core.contracts.TopicRef
import com.engmate.app.feature.quiz.domain.*
import com.engmate.app.feature.quiz.presentation.questionbank.QuestionBankUiState
import com.engmate.app.feature.quiz.presentation.questionbank.QuestionEditor
import org.junit.Assert.*
import org.junit.Test

/** Question bank topics with and without TopicLookup data. */
class QuestionBankTopicsTest {
    private fun question(id: Long, topicId: Long) = Question(id, topicId, null, false, AnswerType.MULTIPLE_CHOICE, "Q$id", null,
        emptyList(), 0)

    @Test fun withoutTopicLookupTheBankKeepsNumericTopics() {
        val state = QuestionBankUiState(questions = listOf(question(1, 5), question(2, 3), question(3, 5)))
        assertFalse(state.topicPicker)
        assertEquals(listOf(3L, 5L), state.topics)
        assertEquals("Chủ đề #5", state.topicLabel(5))
        assertEquals(listOf(1L, 3L), state.copy(category = QuestionCategory.VOCABULARY, topicFilter = 5).visible.map { it.id })
    }

    @Test fun withTopicLookupTheBankShowsNamesAndKeepsUnknownIds() {
        val state = QuestionBankUiState(questions = listOf(question(1, 5)), topicRefs = listOf(TopicRef(9, "Du lịch")))
        assertTrue(state.topicPicker)
        assertEquals(listOf(5L, 9L), state.topics)
        assertEquals("Du lịch", state.topicLabel(9))
        assertEquals("Chủ đề #5", state.topicLabel(5))
    }

    @Test fun missingTopicMessageMatchesTheInputMode() {
        val editor = QuestionEditor(category = QuestionCategory.VOCABULARY)
        assertEquals("Nhập mã chủ đề từ vựng (số).", editor.validate()[QuestionField.CATEGORY])
        assertEquals("Chọn chủ đề từ vựng cho câu hỏi.", editor.validate(topicPicker = true)[QuestionField.CATEGORY])
        assertNull(editor.copy(topicText = "9").validate(topicPicker = true)[QuestionField.CATEGORY])
    }
}
