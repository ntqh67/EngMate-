package com.engmate.app.feature.profile.data.local
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE uid = :uid")
    fun observe(uid: String): Flow<UserProfileEntity?>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: UserProfileEntity): Long
    @Query("UPDATE user_profile SET display_name = :name, photo_url = :photo, english_level = :level, daily_goal_minutes = :goal, updated_at = :now WHERE uid = :uid")
    suspend fun update(uid: String, name: String, photo: String?, level: String, goal: Int, now: Long): Int
    @Query("DELETE FROM user_profile WHERE uid = :uid")
    suspend fun delete(uid: String): Int
}
