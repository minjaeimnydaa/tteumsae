package com.tteumsae.app.ui.account

import com.tteumsae.app.data.account.AccountDeletionClient
import com.tteumsae.app.data.account.DeleteAccountResult
import com.tteumsae.app.data.auth.AuthGateway
import com.tteumsae.app.data.auth.AuthGatewayStatus
import com.tteumsae.app.data.auth.AuthRepository
import com.tteumsae.app.data.auth.AuthStartResult
import com.tteumsae.app.data.profile.OAuthProfileMetadata
import com.tteumsae.app.data.profile.OAuthProfileMetadataSource
import com.tteumsae.app.data.profile.ProfileDto
import com.tteumsae.app.data.profile.ProfileInsertDto
import com.tteumsae.app.data.profile.ProfileRemoteDataSource
import com.tteumsae.app.data.profile.ProfileRepository
import com.tteumsae.app.data.profile.ProfileUpdateDto
import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.account.AgeGroup
import com.tteumsae.app.domain.account.Gender
import com.tteumsae.app.domain.account.LoginProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountViewModelTest {
    @Test
    fun guest_can_open_optional_login_sheet_and_cancel_without_affecting_guest_state() = runBlocking {
        val fixture = fixture()
        fixture.gateway.emit(AuthGatewayStatus.SignedOut)
        fixture.viewModel.awaitState { it.session is AccountSession.Guest }

        fixture.viewModel.openLogin()
        assertTrue(fixture.viewModel.state.value.showLoginSheet)

        fixture.viewModel.dismissLogin()
        assertFalse(fixture.viewModel.state.value.showLoginSheet)
        assertTrue(fixture.viewModel.state.value.session is AccountSession.Guest)
    }

    @Test
    fun signed_in_session_loads_the_owner_profile() = runBlocking {
        val fixture = fixture(profile = profileDto(displayName = "신홍"))

        fixture.gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.KAKAO))

        val state = fixture.viewModel.awaitState { it.profile?.displayName == "신홍" }
        assertEquals(LoginProvider.KAKAO, (state.session as AccountSession.SignedIn).provider)
        assertEquals("user-123", fixture.profileRemote.lastFindUserId)
    }

    @Test
    fun nullable_demographics_can_be_saved() = runBlocking {
        val fixture = fixture(profile = profileDto(displayName = "기존"))
        fixture.gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.GOOGLE))
        fixture.viewModel.awaitState { it.profile != null }

        fixture.viewModel.saveProfile(
            displayName = "새 이름",
            ageGroup = null,
            gender = Gender.PREFER_NOT_TO_SAY,
        )

        val state = fixture.viewModel.awaitState { it.profile?.displayName == "새 이름" }
        assertNull(state.profile?.ageGroup)
        assertEquals(Gender.PREFER_NOT_TO_SAY, state.profile?.gender)
    }

    @Test
    fun sign_out_returns_to_guest() = runBlocking {
        val fixture = fixture(profile = profileDto())
        fixture.gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.KAKAO))
        fixture.viewModel.awaitState { it.session is AccountSession.SignedIn }

        fixture.viewModel.signOut()

        assertTrue(fixture.viewModel.awaitState { it.session is AccountSession.Guest }.session is AccountSession.Guest)
        assertEquals(1, fixture.gateway.signOutCalls)
    }

    @Test
    fun deletion_requires_two_confirmations_and_a_fresh_login_event() = runBlocking {
        val fixture = fixture(
            profile = profileDto(),
            deleteResult = DeleteAccountResult.Success,
        )
        fixture.gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.KAKAO))
        fixture.viewModel.awaitState { it.profile != null }

        fixture.viewModel.requestDeletion()
        assertEquals(AccountDeletionStep.FIRST_CONFIRMATION, fixture.viewModel.state.value.deletionStep)
        fixture.viewModel.confirmDeletionConsequences()
        assertEquals(AccountDeletionStep.FINAL_CONFIRMATION, fixture.viewModel.state.value.deletionStep)
        fixture.viewModel.requireReauthentication()
        assertEquals(AccountDeletionStep.REAUTHENTICATION, fixture.viewModel.state.value.deletionStep)
        assertEquals(0, fixture.deletionClient.calls)

        fixture.viewModel.reauthenticateForDeletion()
        assertEquals(0, fixture.deletionClient.calls)
        fixture.gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.KAKAO))

        fixture.viewModel.awaitState { it.session is AccountSession.Guest }
        assertEquals(1, fixture.deletionClient.calls)
        assertEquals(1, fixture.gateway.signOutCalls)
    }

    @Test
    fun deletion_server_failure_preserves_the_signed_in_session() = runBlocking {
        val fixture = fixture(
            profile = profileDto(),
            deleteResult = DeleteAccountResult.Retryable,
        )
        fixture.gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.GOOGLE))
        fixture.viewModel.awaitState { it.profile != null }
        fixture.viewModel.requestDeletion()
        fixture.viewModel.confirmDeletionConsequences()
        fixture.viewModel.requireReauthentication()
        fixture.viewModel.reauthenticateForDeletion()
        fixture.gateway.emit(AuthGatewayStatus.SignedIn("user-123", LoginProvider.GOOGLE))

        val state = fixture.viewModel.awaitState { it.errorMessage != null }
        assertTrue(state.session is AccountSession.SignedIn)
        assertEquals(0, fixture.gateway.signOutCalls)
        assertEquals(AccountDeletionStep.NONE, state.deletionStep)
    }

    private fun fixture(
        profile: ProfileDto? = null,
        deleteResult: DeleteAccountResult = DeleteAccountResult.Success,
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gateway = FakeAccountAuthGateway()
        val auth = AuthRepository(gateway, scope)
        val profileRemote = FakeAccountProfileRemote(profile)
        val profiles = ProfileRepository(
            remote = profileRemote,
            metadataSource = OAuthProfileMetadataSource {
                OAuthProfileMetadata(displayName = "소셜 닉네임", avatarUrl = null)
            },
        )
        val deletionClient = FakeDeletionClient(deleteResult)
        return Fixture(
            viewModel = AccountViewModel(auth, profiles, deletionClient, scope),
            gateway = gateway,
            profileRemote = profileRemote,
            deletionClient = deletionClient,
        )
    }

    private fun profileDto(
        displayName: String? = null,
        ageGroup: AgeGroup? = null,
        gender: Gender? = null,
    ) = ProfileDto(
        userId = "user-123",
        displayName = displayName,
        avatarUrl = null,
        ageGroup = ageGroup?.name,
        gender = gender?.name,
    )
}

private data class Fixture(
    val viewModel: AccountViewModel,
    val gateway: FakeAccountAuthGateway,
    val profileRemote: FakeAccountProfileRemote,
    val deletionClient: FakeDeletionClient,
)

private suspend fun AccountViewModel.awaitState(
    predicate: (AccountUiState) -> Boolean,
): AccountUiState = withTimeout(1_000L) { state.first(predicate) }

private class FakeAccountAuthGateway : AuthGateway {
    private val mutableStatuses = MutableSharedFlow<AuthGatewayStatus>(extraBufferCapacity = 8)
    override val statuses = mutableStatuses
    var signOutCalls = 0
    var accessToken: String? = "test-token"

    override suspend fun signIn(provider: LoginProvider): AuthStartResult = AuthStartResult.Started

    override suspend fun signOut() {
        signOutCalls += 1
        mutableStatuses.emit(AuthGatewayStatus.SignedOut)
    }

    override fun accessToken(): String? = accessToken

    suspend fun emit(status: AuthGatewayStatus) {
        mutableStatuses.emit(status)
    }
}

private class FakeAccountProfileRemote(
    private var profile: ProfileDto?,
) : ProfileRemoteDataSource {
    var lastFindUserId: String? = null

    override suspend fun find(userId: String): ProfileDto? {
        lastFindUserId = userId
        return profile
    }

    override suspend fun insert(profile: ProfileInsertDto): ProfileDto = ProfileDto(
        userId = profile.userId,
        displayName = profile.displayName,
        avatarUrl = profile.avatarUrl,
        ageGroup = profile.ageGroup,
        gender = profile.gender,
    ).also { this.profile = it }

    override suspend fun update(userId: String, profile: ProfileUpdateDto): ProfileDto = ProfileDto(
        userId = userId,
        displayName = profile.displayName,
        avatarUrl = profile.avatarUrl,
        ageGroup = profile.ageGroup,
        gender = profile.gender,
    ).also { this.profile = it }
}

private class FakeDeletionClient(
    private val result: DeleteAccountResult,
) : AccountDeletionClient {
    var calls = 0

    override suspend fun deleteCurrentAccount(accessToken: String): DeleteAccountResult {
        calls += 1
        return result
    }
}
