package com.tteumsae.app.data.auth

import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.account.LoginProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun disabled_auth_keeps_the_app_in_guest_mode_without_a_client() = runBlocking {
        val repository = repository(DisabledAuthGateway())

        assertEquals(AccountSession.Guest, repository.sessions.value)
        assertNull(SupabaseClientProvider.createOrNull(enabled = false, url = "", publishableKey = ""))
    }

    @Test
    fun initial_state_is_restoring_then_missing_stored_session_becomes_guest() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = repository(gateway)

        assertEquals(AccountSession.Restoring, repository.sessions.value)

        gateway.emit(AuthGatewayStatus.SignedOut)

        assertEquals(AccountSession.Guest, repository.awaitSession<AccountSession.Guest>())
    }

    @Test
    fun authenticated_gateway_event_exposes_domain_signed_in_state() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = repository(gateway)

        gateway.emit(
            AuthGatewayStatus.SignedIn(
                userId = "user-123",
                provider = LoginProvider.KAKAO,
            ),
        )

        assertEquals(
            AccountSession.SignedIn("user-123", LoginProvider.KAKAO),
            repository.awaitSession<AccountSession.SignedIn>(),
        )
    }

    @Test
    fun refresh_failure_is_recoverable_and_does_not_sign_out_or_clear_guest_state() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = repository(gateway)

        gateway.emit(AuthGatewayStatus.RefreshFailed)

        assertTrue(repository.awaitSession<AccountSession.AuthUnavailable>().message.isNotBlank())
        assertEquals(0, gateway.signOutCalls)
    }

    @Test
    fun oauth_error_event_is_exposed_as_a_safe_message_without_sdk_details() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = repository(gateway)

        gateway.emit(AuthGatewayStatus.LoginFailed)

        val failure = repository.awaitSession<AccountSession.AuthUnavailable>()
        assertTrue(failure.message.contains("로그인"))
        assertTrue(failure.message.contains("다시 시도"))
    }

    @Test
    fun explicit_sign_out_returns_to_guest() = runBlocking {
        val gateway = FakeAuthGateway()
        val repository = repository(gateway)
        gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.GOOGLE))
        repository.awaitSession<AccountSession.SignedIn>()

        repository.signOut()

        assertEquals(AccountSession.Guest, repository.awaitSession<AccountSession.Guest>())
        assertEquals(1, gateway.signOutCalls)
    }

    @Test
    fun cancelled_login_returns_to_guest_without_an_error() = runBlocking {
        val gateway = FakeAuthGateway(signInResult = AuthStartResult.Cancelled)
        val repository = repository(gateway)
        gateway.emit(AuthGatewayStatus.SignedOut)
        repository.awaitSession<AccountSession.Guest>()

        repository.signIn(LoginProvider.KAKAO)

        assertEquals(AccountSession.Guest, repository.sessions.value)
        assertEquals(listOf(LoginProvider.KAKAO), gateway.signInProviders)
    }

    @Test
    fun access_token_is_read_only_through_the_gateway() {
        val repository = repository(FakeAuthGateway(accessToken = "token-for-test"))

        assertEquals("token-for-test", repository.accessToken())
    }

    private fun repository(gateway: AuthGateway): AuthRepository = AuthRepository(
        gateway = gateway,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private suspend inline fun <reified T : AccountSession> AuthRepository.awaitSession(): T =
        withTimeout(1_000L) { sessions.first { it is T } as T }
}

private class FakeAuthGateway(
    private val signInResult: AuthStartResult = AuthStartResult.Started,
    private val accessToken: String? = null,
) : AuthGateway {
    private val mutableStatuses = MutableSharedFlow<AuthGatewayStatus>(extraBufferCapacity = 8)
    override val statuses = mutableStatuses
    val signInProviders = mutableListOf<LoginProvider>()
    var signOutCalls = 0

    override suspend fun signIn(provider: LoginProvider): AuthStartResult {
        signInProviders += provider
        return signInResult
    }

    override suspend fun signOut() {
        signOutCalls += 1
        mutableStatuses.emit(AuthGatewayStatus.SignedOut)
    }

    override fun accessToken(): String? = accessToken

    suspend fun emit(status: AuthGatewayStatus) {
        mutableStatuses.emit(status)
    }
}
