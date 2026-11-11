package com.engmate.app.feature.quiz

import com.engmate.app.feature.quiz.domain.*
import org.junit.Assert.*
import org.junit.Test

class QuestionValidatorTest {
    private fun mc(vararg options: Pair<String, Boolean>, lessonId: Long? = 1, topicId: Long? = null, placement: Boolean = false,
                   content: String = "She ___ to school.") =
        QuestionInput(topicId, lessonId, placement, AnswerType.MULTIPLE_CHOICE, content, "",
            options.map { OptionInput(it.first, it.second) })

    private fun fb(vararg accepted: String, correct: Boolean = true) =
        QuestionInput(lessonId = 1, answerType = AnswerType.FILL_BLANK, content = "He ___ (watch) TV.",
            options = accepted.map { OptionInput(it, correct) })

    private val valid = arrayOf("go" to false, "goes" to true, "going" to false, "gone" to false)

    @Test fun validMultipleChoicePasses() = assertTrue(QuestionValidator.errors(mc(*valid)).isEmpty())

    @Test fun mustBelongToExactlyOneCategory() {
        assertEquals(setOf(QuestionField.CATEGORY), QuestionValidator.errors(mc(*valid, lessonId = null)).keys)
        assertEquals(setOf(QuestionField.CATEGORY), QuestionValidator.errors(mc(*valid, lessonId = 1, topicId = 2)).keys)
        assertEquals(setOf(QuestionField.CATEGORY), QuestionValidator.errors(mc(*valid, lessonId = 1, placement = true)).keys)
        assertTrue(QuestionValidator.errors(mc(*valid, lessonId = null, topicId = 2)).isEmpty())
        assertTrue(QuestionValidator.errors(mc(*valid, lessonId = null, placement = true)).isEmpty())
    }

    @Test fun contentMustNotBeBlank() {
        assertEquals("Nội dung câu hỏi không được để trống.", QuestionValidator.errors(mc(*valid, content = "   "))[QuestionField.CONTENT])
    }

    @Test fun multipleChoiceNeedsExactlyFourOptions() {
        val errors = QuestionValidator.errors(mc("a" to true, "b" to false, "c" to false))
        assertEquals("Câu trắc nghiệm cần đúng 4 đáp án.", errors[QuestionField.OPTIONS])
    }

    @Test fun multipleChoiceOptionsNotBlankOrDuplicated() {
        assertEquals("Đáp án không được để trống.",
            QuestionValidator.errors(mc("go" to false, " " to false, "goes" to true, "gone" to false))[QuestionField.OPTIONS])
        assertEquals("Các đáp án không được trùng nhau.",
            QuestionValidator.errors(mc("go" to false, " GO " to false, "goes" to true, "gone" to false))[QuestionField.OPTIONS])
    }

    @Test fun multipleChoiceNeedsExactlyOneCorrect() {
        val none = QuestionValidator.errors(mc("go" to false, "goes" to false, "going" to false, "gone" to false))
        val two = QuestionValidator.errors(mc("go" to true, "goes" to true, "going" to false, "gone" to false))
        assertEquals("Câu trắc nghiệm cần đúng 1 đáp án đúng.", none[QuestionField.CORRECT])
        assertEquals("Câu trắc nghiệm cần đúng 1 đáp án đúng.", two[QuestionField.CORRECT])
    }

    @Test fun fillBlankRules() {
        assertTrue(QuestionValidator.errors(fb("watches")).isEmpty())
        assertTrue(QuestionValidator.errors(fb("are not watching", "aren't watching")).isEmpty())
        assertEquals("Câu điền từ cần ít nhất 1 đáp án chấp nhận.", QuestionValidator.errors(fb())[QuestionField.OPTIONS])
        assertEquals("Đáp án không được để trống.", QuestionValidator.errors(fb("watches", ""))[QuestionField.OPTIONS])
        assertEquals("Các đáp án không được trùng nhau.", QuestionValidator.errors(fb("watches", "Watches "))[QuestionField.OPTIONS])
        assertEquals("Mọi đáp án của câu điền từ phải là đáp án đúng.", QuestionValidator.errors(fb("watches", correct = false))[QuestionField.CORRECT])
    }
}
