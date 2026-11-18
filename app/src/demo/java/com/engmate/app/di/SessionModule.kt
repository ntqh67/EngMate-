package com.engmate.app.di

import com.engmate.app.feature.auth.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

class DemoSession : UserSession, AuthService {
    override val isDemo = true
    override val user = MutableStateFlow<SessionUser?>(SessionUser("local-demo-user", "demo@engmate.local", "Bạn học EngMate"))
    override suspend fun signIn(email: String, password: String) { user.value = SessionUser("local-demo-user", "demo@engmate.local", "Bạn học EngMate") }
    override suspend fun register(name: String, email: String, password: String) { throw IllegalStateException("Bản demo không tạo tài khoản. Hãy dùng bản live đã cấu hình Firebase.") }
    override suspend fun resetPassword(email: String) { throw IllegalStateException("Bản demo không gửi email. Hãy dùng bản live đã cấu hình Firebase.") }
    override fun signOut() { user.value = null }
}
@Module @InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides @Singleton fun demo() = DemoSession()
    @Provides fun session(demo: DemoSession): UserSession = demo
    @Provides fun auth(demo: DemoSession): AuthService = demo
}
