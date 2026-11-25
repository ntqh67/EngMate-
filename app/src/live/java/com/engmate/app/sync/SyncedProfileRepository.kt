package com.engmate.app.sync

import android.util.Log
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.profile.data.RoomProfileRepository
import com.engmate.app.feature.profile.data.local.UserProfileDao
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.profile.domain.ProfileInput
import com.engmate.app.feature.profile.domain.ProfileRepository
import com.engmate.app.feature.profile.domain.ProfileValidator
import com.engmate.app.feature.profile.domain.UserProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnCompleteListener { t ->
        if (!c.isActive) return@addOnCompleteListener
        if (t.isSuccessful) c.resume(t.result) else c.resumeWithException(t.exception ?: IllegalStateException("Firestore lỗi"))
    }
}

/**
 * Offline-first profile: Room stays the source the UI reads and every edit is validated and saved there first,
 * then mirrored to Firestore at users/{uid}/profile/main (the newer `updatedAt` wins).
 *
 * - Opening the profile pulls the cloud copy: it fills an empty device (new phone) or replaces an older local copy;
 *   a newer local copy is pushed instead.
 * - Writes are not awaited: Firestore queues them while offline and sends them when the network is back.
 */
@Singleton
class SyncedProfileRepository @Inject constructor(
    private val local: RoomProfileRepository,
    private val dao: UserProfileDao,
    private val session: UserSession,
    private val content: FirestoreContent,
) : ProfileRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun ref(uid: String): DocumentReference? =
        content.firestore?.collection("users")?.document(uid)?.collection("profile")?.document("main")

    override fun observe(uid: String): Flow<UserProfile?> =
        local.observe(uid).onStart { scope.launch { runCatching { pull(uid) }.onFailure { Log.w(FirestoreContent.TAG, "Profile sync failed", it) } } }

    suspend fun pull(uid: String) {
        val doc = ref(uid) ?: return
        if (session.user.value?.uid != uid) return
        val snap = doc.get().await()
        val mine = dao.get(uid)
        if (!snap.exists()) { mine?.let { doc.set(it.toRemote()) }; return }
        val remoteUpdated = snap.getLong("updatedAt") ?: 0L
        if (mine != null && mine.updatedAt >= remoteUpdated) {
            if (mine.updatedAt > remoteUpdated) doc.set(mine.toRemote())
            return
        }
        val input = ProfileInput(snap.getString("displayName").orEmpty(), snap.getString("photoUrl").orEmpty(),
            snap.getString("englishLevel").orEmpty(), (snap.getLong("dailyGoalMinutes") ?: 0L).toString())
        if (ProfileValidator.errors(input).isNotEmpty()) return // a bad console edit must not reach the device
        val v = input.normalized()
        if (session.user.value?.uid != uid) return
        if (mine == null) {
            val now = System.currentTimeMillis()
            dao.insert(UserProfileEntity(uid, snap.getString("email") ?: session.user.value?.email.orEmpty(), v.displayName,
                v.photoUrl.ifBlank { null }, v.englishLevel, v.dailyGoalMinutes.toInt(), snap.getLong("createdAt") ?: now, remoteUpdated))
        } else {
            dao.overwrite(uid, v.displayName, v.photoUrl.ifBlank { null }, v.englishLevel, v.dailyGoalMinutes.toInt(), remoteUpdated)
        }
    }

    private suspend fun push(uid: String) {
        val entity = dao.get(uid) ?: return
        ref(uid)?.set(entity.toRemote())?.addOnFailureListener { FirestoreContent.log("put profile", it) }
    }

    override suspend fun create(uid: String, input: ProfileInput) { local.create(uid, input); push(uid) }
    override suspend fun update(uid: String, input: ProfileInput) { local.update(uid, input); push(uid) }
    override suspend fun delete(uid: String) {
        local.delete(uid)
        ref(uid)?.delete()?.addOnFailureListener { FirestoreContent.log("delete profile", it) }
    }
}

private fun UserProfileEntity.toRemote(): Map<String, Any?> = mapOf(
    "email" to email, "displayName" to displayName, "photoUrl" to photoUrl, "englishLevel" to englishLevel,
    "dailyGoalMinutes" to dailyGoalMinutes, "createdAt" to createdAt, "updatedAt" to updatedAt,
)
