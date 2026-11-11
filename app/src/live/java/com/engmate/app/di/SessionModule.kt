package com.engmate.app.di
import android.content.Context
import com.engmate.app.feature.auth.domain.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
class FirebaseUserSession(context: Context) : UserSession {
    override val isDemo = false
    private val state = MutableStateFlow<SessionUser?>(null)
    override val user = state.asStateFlow()
    // Singleton listener lives for the application process lifetime.
    private val listener = FirebaseAuth.AuthStateListener { auth ->
        state.value = auth.currentUser?.let { SessionUser(it.uid, it.email.orEmpty(), it.displayName.orEmpty()) }
    }
    init {
        if (FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null) {
            FirebaseAuth.getInstance().addAuthStateListener(listener)
        }
    }
}
@Module @InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides @Singleton fun session(@ApplicationContext context: Context): UserSession = FirebaseUserSession(context)
}
