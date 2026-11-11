package com.engmate.app.feature.profile.domain
import kotlinx.coroutines.flow.Flow
interface ProfileRepository {
    fun observe(uid: String): Flow<UserProfile?>
    suspend fun create(uid: String, input: ProfileInput)
    suspend fun update(uid: String, input: ProfileInput)
    suspend fun delete(uid: String)
}
