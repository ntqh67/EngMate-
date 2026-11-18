package com.engmate.app.feature.progress.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StudyDao {
    @Insert protected abstract suspend fun insertActivity(activity: LearningActivityEntity)
    @Update protected abstract suspend fun updateActivity(activity: LearningActivityEntity): Int
    @Query("DELETE FROM learning_activity WHERE user_uid = :uid AND activity_id = :id")
    protected abstract suspend fun removeActivity(uid: String, id: String): Int
    @Query("SELECT * FROM learning_activity WHERE activity_id = :id")
    protected abstract suspend fun getById(id: String): LearningActivityEntity?
    @Query("SELECT * FROM learning_activity WHERE user_uid = :uid AND activity_id = :id")
    abstract suspend fun getActivity(uid: String, id: String): LearningActivityEntity?
    @Query("SELECT * FROM learning_activity WHERE user_uid = :uid AND study_date BETWEEN :fromDate AND :toDate ORDER BY occurred_at DESC, activity_id")
    abstract fun observeActivities(uid: String, fromDate: String, toDate: String): Flow<List<LearningActivityEntity>>
    @Query("SELECT * FROM daily_study_stat WHERE user_uid = :uid AND study_date BETWEEN :fromDate AND :toDate ORDER BY study_date")
    abstract fun observeStats(uid: String, fromDate: String, toDate: String): Flow<List<DailyStudyStatEntity>>
    @Query("SELECT * FROM daily_study_stat WHERE user_uid = :uid ORDER BY study_date")
    abstract fun observeAllStats(uid: String): Flow<List<DailyStudyStatEntity>>
    @Query("SELECT * FROM daily_study_stat WHERE user_uid = :uid AND study_date = :date")
    abstract suspend fun getStat(uid: String, date: String): DailyStudyStatEntity?
    @Query("SELECT COUNT(*) FROM learning_activity WHERE user_uid = :uid AND study_date = :date")
    protected abstract suspend fun activityCount(uid: String, date: String): Int
    @Query("SELECT :uid AS user_uid, :date AS study_date, coalesce(SUM(xp_earned), 0) AS xp_earned, coalesce(SUM(duration_seconds), 0) AS study_seconds, coalesce(SUM(words_learned), 0) AS words_learned, coalesce(SUM(words_reviewed), 0) AS words_reviewed, coalesce(SUM(quizzes_completed), 0) AS quizzes_completed FROM learning_activity WHERE user_uid = :uid AND study_date = :date")
    protected abstract suspend fun aggregate(uid: String, date: String): DailyStudyStatEntity
    @Upsert protected abstract suspend fun saveStat(stat: DailyStudyStatEntity)
    @Query("DELETE FROM daily_study_stat WHERE user_uid = :uid AND study_date = :date")
    protected abstract suspend fun deleteStat(uid: String, date: String)

    private fun validate(activity: LearningActivityEntity) {
        require(activity.activityId.isNotBlank() && activity.userUid.isNotBlank())
        require(activity.xpEarned >= 0 && activity.durationSeconds >= 0 && activity.occurredAt >= 0)
        require(activity.wordsLearned >= 0 && activity.wordsReviewed >= 0 && activity.quizzesCompleted in 0..1)
        // A fixed local date is stored at completion time; timezone changes do not move history.
        require(activity.studyDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        require(runCatching { java.time.LocalDate.parse(activity.studyDate) }.isSuccess) { "Invalid calendar date" }
    }
    private suspend fun rebuild(uid: String, date: String) {
        if (activityCount(uid, date) == 0) deleteStat(uid, date) else saveStat(aggregate(uid, date))
    }
    @Transaction
    open suspend fun record(activity: LearningActivityEntity): Boolean {
        validate(activity)
        val existing = getById(activity.activityId)
        if (existing != null) {
            require(existing == activity) { "Activity ID already used with different data" }
            return false
        }
        insertActivity(activity)
        rebuild(activity.userUid, activity.studyDate)
        return true
    }
    @Transaction
    open suspend fun revise(uid: String, activity: LearningActivityEntity): Boolean {
        require(activity.userUid == uid)
        validate(activity)
        val old = getActivity(uid, activity.activityId) ?: return false
        check(updateActivity(activity) == 1)
        rebuild(uid, old.studyDate)
        if (old.studyDate != activity.studyDate) rebuild(uid, activity.studyDate)
        return true
    }
    @Transaction
    open suspend fun delete(uid: String, id: String): Boolean {
        val old = getActivity(uid, id) ?: return false
        check(removeActivity(uid, id) == 1)
        rebuild(uid, old.studyDate)
        return true
    }
}
