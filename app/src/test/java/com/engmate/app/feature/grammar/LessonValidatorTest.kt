package com.engmate.app.feature.grammar

import com.engmate.app.feature.grammar.domain.*
import org.junit.Assert.*
import org.junit.Test

class LessonValidatorTest {
    private val valid = LessonInput("Hiện tại đơn", "A1", "", "S + V(s/es)", listOf(GrammarExample("I go.", "Tôi đi.")))

    @Test fun validLessonPasses() = assertTrue(LessonValidator.errors(valid).isEmpty())

    @Test fun titleLengthIsTwoToHundredAfterTrim() {
        assertNotNull(LessonValidator.errors(valid.copy(title = " A "))[LessonField.TITLE])
        assertNull(LessonValidator.errors(valid.copy(title = "Ab"))[LessonField.TITLE])
        assertNull(LessonValidator.errors(valid.copy(title = "a".repeat(100)))[LessonField.TITLE])
        assertEquals("Tiêu đề từ 2–100 ký tự.", LessonValidator.errors(valid.copy(title = "a".repeat(101)))[LessonField.TITLE])
    }

    @Test fun levelMustBeA1ToB2() {
        listOf("A1", "A2", "B1", "B2").forEach { assertNull(LessonValidator.errors(valid.copy(level = it))[LessonField.LEVEL]) }
        listOf("C1", "", "a1").forEach { assertNotNull(LessonValidator.errors(valid.copy(level = it))[LessonField.LEVEL]) }
    }

    @Test fun contentMustNotBeBlank() {
        assertEquals("Nội dung bài học không được để trống.", LessonValidator.errors(valid.copy(content = "  \n "))[LessonField.CONTENT])
    }

    @Test fun examplesNeedBothSidesButEmptyRowsAreIgnored() {
        assertNotNull(LessonValidator.errors(valid.copy(examples = listOf(GrammarExample("I go.", ""))))[LessonField.EXAMPLES])
        assertTrue(LessonValidator.errors(valid.copy(examples = listOf(GrammarExample(" ", " ")))).isEmpty())
    }
}
