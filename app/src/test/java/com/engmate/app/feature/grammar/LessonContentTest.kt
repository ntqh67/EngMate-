package com.engmate.app.feature.grammar

import com.engmate.app.feature.grammar.presentation.lesson.LessonBlock
import com.engmate.app.feature.grammar.presentation.lesson.LessonContent
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonContentTest {
    @Test fun `splits a lesson into formula, points, markers and note`() {
        val content = """
            Cấu trúc:
            • Khẳng định: S + V(s/es).
            • Phủ định: S + do/does + not + V.
            • Nghi vấn: Do/Does + S + V?
            Cách dùng:
            • Thói quen: I get up at 6.
            Dấu hiệu: always, usually, every day.
            Quy tắc thêm -es: goes, watches.
            So sánh: A nhấn mạnh kết quả; B nhấn mạnh quá trình.
        """.trimIndent()
        val blocks = LessonContent.parse(content)
        assertEquals(LessonBlock.Formula(listOf("Khẳng định" to "S + V(s/es).", "Phủ định" to "S + do/does + not + V.", "Nghi vấn" to "Do/Does + S + V?")), blocks[0])
        assertEquals(LessonBlock.Points("Cách dùng", null, listOf("Thói quen: I get up at 6.")), blocks[1])
        assertEquals(LessonBlock.Markers(listOf("always", "usually", "every day")), blocks[2])
        assertEquals(LessonBlock.Note("Quy tắc thêm -es", "goes, watches.", emptyList()), blocks[3])
        assertEquals(LessonBlock.Points("So sánh", "A nhấn mạnh kết quả; B nhấn mạnh quá trình.", emptyList()), blocks[4])
    }

    @Test fun `bullets inside a section are not mistaken for headings`() {
        val blocks = LessonContent.parse("Cách dùng:\n• Ví dụ: She goes.\n• Sự thật: Water boils.")
        assertEquals(listOf(LessonBlock.Points("Cách dùng", null, listOf("Ví dụ: She goes.", "Sự thật: Water boils."))), blocks)
    }

    @Test fun `unlabeled content stays a paragraph`() {
        assertEquals(listOf(LessonBlock.Paragraph("Một đoạn văn.\nDòng thứ hai.")), LessonContent.parse("Một đoạn văn.\nDòng thứ hai."))
    }

    @Test fun `title splits into Vietnamese and English`() {
        assertEquals("Hiện tại đơn" to "Present Simple", LessonContent.splitTitle("Hiện tại đơn (Present Simple)"))
        // A list in brackets is part of the name, not an English title.
        assertEquals("Động từ to be (am, is, are)" to null, LessonContent.splitTitle("Động từ to be (am, is, are)"))
        assertEquals(1, LessonContent.readingMinutes("ngắn"))
    }
}
