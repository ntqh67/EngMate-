package com.engmate.app.feature.profile.data
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.profile.data.local.*
import com.engmate.app.feature.profile.domain.*
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class RoomProfileRepository @Inject constructor(
    private val dao: UserProfileDao, private val session: UserSession, private val clock: Clock,
) : ProfileRepository {
    private fun owner(uid: String) = session.user.value?.takeIf { it.uid == uid && uid.isNotBlank() } ?: throw SessionRequired()
    override fun observe(uid: String): Flow<UserProfile?> {
        owner(uid)
        return dao.observe(uid).map { owner(uid); it?.toDomain() }
    }
    private fun validate(input: ProfileInput): ProfileInput {
        val errors = ProfileValidator.errors(input)
        if (errors.isNotEmpty()) throw InvalidProfile(errors)
        return input.normalized()
    }
    override suspend fun create(uid: String, input: ProfileInput) {
        val value = validate(input)
        val user = owner(uid)
        val now = clock.millis()
        val result = dao.insert(UserProfileEntity(uid, user.email, value.displayName,
            value.photoUrl.ifBlank { null }, value.englishLevel, value.dailyGoalMinutes.toInt(), now, now))
        if (result == -1L) throw ProfileAlreadyExists()
    }
    override suspend fun update(uid: String, input: ProfileInput) {
        val value = validate(input)
        owner(uid)
        if (dao.update(uid, value.displayName, value.photoUrl.ifBlank { null }, value.englishLevel,
                value.dailyGoalMinutes.toInt(), clock.millis()) == 0) throw ProfileMissing()
    }
    override suspend fun delete(uid: String) {
        owner(uid)
        if (dao.delete(uid) == 0) throw ProfileMissing()
    }
}
