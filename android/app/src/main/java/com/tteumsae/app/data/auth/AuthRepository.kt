package com.tteumsae.app.data.auth

import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.account.LoginProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthRepository(
    private val gateway: AuthGateway,
    scope: CoroutineScope,
) {
    private val mutableSessions = MutableStateFlow<AccountSession>(AccountSession.Restoring)
    val sessions: StateFlow<AccountSession> = mutableSessions.asStateFlow()
    private val mutableSessionEvents = MutableSharedFlow<AccountSession>(extraBufferCapacity = 8)
    val sessionEvents: SharedFlow<AccountSession> = mutableSessionEvents.asSharedFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.statuses.collect { status ->
                val session = status.toAccountSession()
                mutableSessions.value = session
                mutableSessionEvents.emit(session)
            }
        }
    }

    suspend fun signIn(provider: LoginProvider) {
        try {
            when (val result = gateway.signIn(provider)) {
                AuthStartResult.Started -> Unit
                AuthStartResult.Cancelled -> mutableSessions.value = AccountSession.Guest
                is AuthStartResult.Unavailable -> {
                    mutableSessions.value = AccountSession.AuthUnavailable(result.message)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            mutableSessions.value = AccountSession.AuthUnavailable(NETWORK_ERROR_MESSAGE)
        } catch (_: Exception) {
            mutableSessions.value = AccountSession.AuthUnavailable(GENERIC_ERROR_MESSAGE)
        }
    }

    suspend fun signOut() {
        try {
            gateway.signOut()
            mutableSessions.value = AccountSession.Guest
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            mutableSessions.value = AccountSession.AuthUnavailable(NETWORK_ERROR_MESSAGE)
        } catch (_: Exception) {
            mutableSessions.value = AccountSession.AuthUnavailable(GENERIC_ERROR_MESSAGE)
        }
    }

    suspend fun clearLocalSession() {
        try {
            gateway.clearLocalSession()
        } finally {
            mutableSessions.value = AccountSession.Guest
        }
    }

    fun accessToken(): String? = gateway.accessToken()

    fun reportReauthenticationRequired() {
        mutableSessions.value = AccountSession.NeedsReauthentication
    }

    fun reportLoginFailure() {
        mutableSessions.value = AccountSession.AuthUnavailable(GENERIC_ERROR_MESSAGE)
    }

    private fun AuthGatewayStatus.toAccountSession(): AccountSession = when (this) {
        AuthGatewayStatus.Initializing -> AccountSession.Restoring
        AuthGatewayStatus.SignedOut -> AccountSession.Guest
        is AuthGatewayStatus.SignedIn -> AccountSession.SignedIn(userId, provider)
        AuthGatewayStatus.RefreshFailed -> AccountSession.AuthUnavailable(NETWORK_ERROR_MESSAGE)
        AuthGatewayStatus.LoginFailed -> AccountSession.AuthUnavailable(GENERIC_ERROR_MESSAGE)
        AuthGatewayStatus.NeedsReauthentication -> AccountSession.NeedsReauthentication
    }

    private companion object {
        const val NETWORK_ERROR_MESSAGE =
            "로그인 상태를 확인할 수 없습니다. 네트워크 연결 후 다시 시도해 주세요."
        const val GENERIC_ERROR_MESSAGE = "로그인을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
    }
}
