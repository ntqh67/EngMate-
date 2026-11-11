package com.engmate.app.di
import com.engmate.app.feature.auth.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
@Module @InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides @Singleton fun session(): UserSession = object : UserSession {
        override val isDemo = true
        override val user = MutableStateFlow<SessionUser?>(SessionUser("local-demo-user", "demo@engmate.local", "Bạn học EngMate"))
    }
}
