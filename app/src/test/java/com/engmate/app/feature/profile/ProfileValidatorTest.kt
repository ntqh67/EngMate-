package com.engmate.app.feature.profile
import com.engmate.app.feature.profile.domain.*
import org.junit.Assert.*
import org.junit.Test
class ProfileValidatorTest {
    @Test fun acceptsVietnameseNamesAndGoalBoundaries() {
        listOf("Nguyễn Quốc Huân", "  Nguyễn Phương Đông  ", "Nguyễn Văn An").forEach { name ->
            listOf("5", "120").forEach { goal -> assertTrue(ProfileValidator.errors(ProfileInput(name, dailyGoalMinutes = goal)).isEmpty()) }
        }
    }
    @Test fun rejectsInvalidFields() {
        val errors = ProfileValidator.errors(ProfileInput("A1", "http://example.com/a.png", "Z9", "121"))
        assertEquals(ProfileField.entries.toSet(), errors.keys)
        listOf("", "4", "-1", "1.5", "999999999999999999999").forEach {
            assertTrue(ProfileValidator.errors(ProfileInput("Nguyễn An", dailyGoalMinutes = it)).containsKey(ProfileField.GOAL))
        }
    }
    @Test fun optionalPhotoAndValidHttps() {
        assertTrue(ProfileValidator.errors(ProfileInput("Nguyễn An")).isEmpty())
        assertTrue(ProfileValidator.errors(ProfileInput("Nguyễn An", "https://example.com/avatar.png")).isEmpty())
        assertTrue(ProfileValidator.errors(ProfileInput("Nguyễn An", "https://")).containsKey(ProfileField.PHOTO))
    }
}
