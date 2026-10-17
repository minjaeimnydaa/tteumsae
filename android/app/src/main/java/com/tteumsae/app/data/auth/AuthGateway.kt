package com.tteumsae.app.data.auth

import com.tteumsae.app.domain.account.LoginProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface AuthGateway {
    val statuses: Flow<AuthGatewayStatus>

    suspend fun signIn(provider: LoginProvider): AuthStartResult

    suspend fun signOut()

    suspend fun clearLocalSession() = signOut()

    fun accessToken(): String?
}

sealed interface AuthGatewayStatus {
    data object Initializing : AuthGatewayStatus

    data object SignedOut : AuthGatewayStatus

    data class SignedIn(
        val userId: String,
        val provider: LoginProvider,
    ) : AuthGatewayStatus

    data object RefreshFailed : AuthGatewayStatus

    data object LoginFailed : AuthGatewayStatus

    data object NeedsReauthentication : AuthGatewayStatus
}

sealed interface AuthStartResult {
    data object Started : AuthStartResult

    data object Cancelled : AuthStartResult

    data class Unavailable(val message: String) : AuthStartResult
}

class DisabledAuthGateway : AuthGateway {
    override val statuses = MutableStateFlow<AuthGatewayStatus>(AuthGatewayStatus.SignedOut)

    override suspend fun signIn(provider: LoginProvider): AuthStartResult =
        AuthStartResult.Unavailable("로그인 설정이 아직 완료되지 않았습니다.")

    override suspend fun signOut() = Unit

    override fun accessToken(): String? = null
}
