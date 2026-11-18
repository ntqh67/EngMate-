package com.engmate.app.feature.auth.domain

interface AuthService {
    suspend fun signIn(email: String, password: String)
    suspend fun register(name: String, email: String, password: String)
    suspend fun resetPassword(email: String)
    fun signOut()
}
