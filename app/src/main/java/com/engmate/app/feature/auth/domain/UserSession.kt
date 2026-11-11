package com.engmate.app.feature.auth.domain
import kotlinx.coroutines.flow.StateFlow
data class SessionUser(val uid: String, val email: String, val suggestedName: String = "")
interface UserSession {
    val user: StateFlow<SessionUser?>
    val isDemo: Boolean
}
