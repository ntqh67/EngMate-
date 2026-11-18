package com.engmate.app.di

import android.content.Context
import com.engmate.app.feature.auth.domain.*
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IllegalStateException("Thao tác xác thực thất bại."))
    }
}
class FirebaseUserSession(context: Context) : UserSession, AuthService {
    override val isDemo = false
    private val state = MutableStateFlow<SessionUser?>(null)
    override val user = state.asStateFlow()
    private val auth: FirebaseAuth? = if (FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null) FirebaseAuth.getInstance() else null
    private fun firebase() = auth ?: throw IllegalStateException("Chưa cấu hình Firebase cho ứng dụng.")
    init {
        auth?.addAuthStateListener { current ->
            state.value = current.currentUser?.takeIf { it.isEmailVerified }?.let { SessionUser(it.uid, it.email.orEmpty(), it.displayName.orEmpty()) }
        }
    }
    override suspend fun signIn(email: String, password: String) {
        val firebase = firebase()
        val user = firebase.signInWithEmailAndPassword(email, password).awaitResult().user ?: error("Không tìm thấy tài khoản.")
        user.reload().awaitResult()
        if (!user.isEmailVerified) {
            val sent = try { user.sendEmailVerification().awaitResult(); true } catch (_: Exception) { false }
            firebase.signOut()
            throw IllegalStateException(if (sent) "Email chưa được xác minh. Đã gửi lại email xác minh; hãy kiểm tra hộp thư." else "Email chưa được xác minh. Không gửi lại được email, vui lòng thử sau.")
        }
        state.value = SessionUser(user.uid, user.email.orEmpty(), user.displayName.orEmpty())
    }
    override suspend fun register(name: String, email: String, password: String) {
        val firebase = firebase()
        val user = firebase.createUserWithEmailAndPassword(email, password).awaitResult().user ?: error("Không tạo được tài khoản.")
        try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).awaitResult()
            user.sendEmailVerification().awaitResult()
        } catch (e: Exception) {
            throw IllegalStateException("Tài khoản đã tạo nhưng chưa gửi được email xác minh. Hãy đăng nhập lại để nhận hướng dẫn.", e)
        } finally { firebase.signOut() }
    }
    override suspend fun resetPassword(email: String) { firebase().sendPasswordResetEmail(email).awaitResult() }
    override fun signOut() { auth?.signOut(); state.value = null }
}
@Module @InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides @Singleton fun firebaseSession(@ApplicationContext context: Context) = FirebaseUserSession(context)
    @Provides fun session(session: FirebaseUserSession): UserSession = session
    @Provides fun auth(session: FirebaseUserSession): AuthService = session
}
