package com.engmate.app.feature.notebook

import com.engmate.app.core.database.WordLearningStatus
import com.engmate.app.feature.notebook.domain.HandbookEntry
import com.engmate.app.feature.notebook.presentation.filterEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class NotebookFilterTest {
    private val entries = listOf(
        HandbookEntry(1, "camera", "a device for photos", "máy ảnh", null, WordLearningStatus.NEW),
        HandbookEntry(2, "travel", "to go somewhere", null, "đi du lịch", WordLearningStatus.LEARNING),
        HandbookEntry(3, "apple", "a fruit", null, null, WordLearningStatus.MASTERED),
    )

    @Test fun `blank query keeps every word`() = assertEquals(entries, filterEntries(entries, "  "))

    @Test fun `matches the word or the shown meaning, ignoring case`() {
        assertEquals(listOf(1L), filterEntries(entries, "CAM").map { it.cardId })
        assertEquals(listOf(2L), filterEntries(entries, "du lịch").map { it.cardId }) // the learner's own meaning
        assertEquals(listOf(3L), filterEntries(entries, "fruit").map { it.cardId })   // falls back to the card meaning
        assertEquals(emptyList<Long>(), filterEntries(entries, "xyz").map { it.cardId })
    }
}
