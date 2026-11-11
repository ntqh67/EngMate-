package com.engmate.app.feature.profile.data.local
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.engmate.app.feature.profile.domain.UserProfile
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val email: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String?,
    @ColumnInfo(name = "english_level") val englishLevel: String,
    @ColumnInfo(name = "daily_goal_minutes") val dailyGoalMinutes: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun toDomain() = UserProfile(uid, email, displayName, photoUrl, englishLevel, dailyGoalMinutes, createdAt, updatedAt)
}
