package com.tteumsae.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tteumsae.app.data.account.AccountDeletionClient
import com.tteumsae.app.data.account.DeleteAccountResult
import com.tteumsae.app.data.auth.AuthRepository
import com.tteumsae.app.data.profile.ProfileRepository
import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.account.AgeGroup
import com.tteumsae.app.domain.account.Gender
import com.tteumsae.app.domain.account.LoginProvider
import com.tteumsae.app.domain.account.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountDeletionStep {
    NONE,
    FIRST_CONFIRMATION,
    FINAL_CONFIRMATION,
    REAUTHENTICATION,
}

data class AccountUiState(
    val session: AccountSession = AccountSession.Restoring,
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val showLoginSheet: Boolean = false,
    val deletionStep: AccountDeletionStep = AccountDeletionStep.NONE,
    val errorMessage: String? = null,
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository?,
    private val deletionClient: AccountDeletionClient,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val accountScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutableState.asStateFlow()
    private var loadedUserId: String? = null
    private var deleteAfterNextLogin = false
    private var deletionInProgress = false

    init {
        accountScope.launch {
            authRepository.sessions.collect(::handleSession)
        }
        accountScope.launch {
            authRepository.sessionEvents.collect { session ->
                if (deleteAfterNextLogin && session is AccountSession.SignedIn) {
                    deleteAfterNextLogin = false
                    deleteCurrentAccount()
                }
            }
        }
    }

    fun openLogin() {
        mutableState.update { it.copy(showLoginSheet = true, errorMessage = null) }
    }

    fun dismissLogin() {
        mutableState.update { it.copy(showLoginSheet = false) }
    }

    fun signIn(provider: LoginProvider) {
        mutableState.update { it.copy(showLoginSheet = false, isLoading = true, errorMessage = null) }
        accountScope.launch {
            authRepository.signIn(provider)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun signOut() {
        accountScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.signOut()
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun saveProfile(displayName: String?, ageGroup: AgeGroup?, gender: Gender?) {
        val current = mutableState.value.profile ?: return
        val repository = profileRepository ?: return
        accountScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val updated = repository.update(
                    current.copy(
                        displayName = displayName,
                        ageGroup = ageGroup,
                        gender = gender,
                    ),
                )
                mutableState.update { it.copy(profile = updated, isLoading = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "프로필을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    )
                }
            }
        }
    }

    fun requestDeletion() {
        if (mutableState.value.session !is AccountSession.SignedIn) return
        mutableState.update {
            it.copy(deletionStep = AccountDeletionStep.FIRST_CONFIRMATION, errorMessage = null)
        }
    }

    fun confirmDeletionConsequences() {
        if (mutableState.value.deletionStep != AccountDeletionStep.FIRST_CONFIRMATION) return
        mutableState.update { it.copy(deletionStep = AccountDeletionStep.FINAL_CONFIRMATION) }
    }

    fun requireReauthentication() {
        if (mutableState.value.deletionStep != AccountDeletionStep.FINAL_CONFIRMATION) return
        mutableState.update { it.copy(deletionStep = AccountDeletionStep.REAUTHENTICATION) }
    }

    fun reauthenticateForDeletion() {
        val session = mutableState.value.session as? AccountSession.SignedIn ?: return
        if (mutableState.value.deletionStep != AccountDeletionStep.REAUTHENTICATION) return
        if (deleteAfterNextLogin) return
        deleteAfterNextLogin = true
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        accountScope.launch {
            authRepository.signIn(session.provider)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun cancelDeletion() {
        deleteAfterNextLogin = false
        mutableState.update {
            it.copy(deletionStep = AccountDeletionStep.NONE, isLoading = false, errorMessage = null)
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun retryProfileLoad() {
        val session = mutableState.value.session as? AccountSession.SignedIn ?: return
        if (mutableState.value.isLoading) return
        viewModelScope.launch { loadProfile(session) }
    }

    private suspend fun handleSession(session: AccountSession) {
        when (session) {
            AccountSession.Guest -> {
                loadedUserId = null
                mutableState.update {
                    it.copy(
                        session = session,
                        profile = null,
                        isLoading = false,
                        showLoginSheet = false,
                        deletionStep = AccountDeletionStep.NONE,
                    )
                }
            }

            is AccountSession.SignedIn -> {
                mutableState.update { it.copy(session = session, errorMessage = null) }
                if (loadedUserId != session.userId) loadProfile(session)
            }

            is AccountSession.AuthUnavailable -> mutableState.update {
                it.copy(session = session, isLoading = false, errorMessage = session.message)
            }

            AccountSession.NeedsReauthentication -> mutableState.update {
                it.copy(
                    session = session,
                    isLoading = false,
                    errorMessage = "로그인을 다시 확인해 주세요.",
                )
            }

            AccountSession.Restoring -> mutableState.update {
                it.copy(session = session, isLoading = true)
            }
        }
    }

    private suspend fun loadProfile(session: AccountSession.SignedIn) {
        val repository = profileRepository ?: return
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val profile = repository.loadOrCreate(session)
            loadedUserId = session.userId
            mutableState.update { it.copy(profile = profile, isLoading = false) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "프로필을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
                )
            }
        }
    }

    private suspend fun deleteCurrentAccount() {
        if (deletionInProgress) return
        deletionInProgress = true
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        val token = authRepository.accessToken()
        val result = try {
            if (token.isNullOrBlank()) {
                DeleteAccountResult.NeedsLogin
            } else {
                deletionClient.deleteCurrentAccount(token)
            }
        } catch (cancellation: CancellationException) {
            deletionInProgress = false
            throw cancellation
        } catch (_: Exception) {
            DeleteAccountResult.Retryable
        }
        when (result) {
            DeleteAccountResult.Success -> {
                authRepository.clearLocalSession()
                loadedUserId = null
                mutableState.update {
                    it.copy(
                        profile = null,
                        isLoading = false,
                        deletionStep = AccountDeletionStep.NONE,
                    )
                }
            }

            DeleteAccountResult.NeedsLogin -> mutableState.update {
                it.copy(
                    isLoading = false,
                    deletionStep = AccountDeletionStep.REAUTHENTICATION,
                    errorMessage = "계정 보호를 위해 다시 로그인해 주세요.",
                )
            }

            DeleteAccountResult.Retryable -> mutableState.update {
                it.copy(
                    isLoading = false,
                    deletionStep = AccountDeletionStep.NONE,
                    errorMessage = "계정을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                )
            }

            DeleteAccountResult.Failed -> mutableState.update {
                it.copy(
                    isLoading = false,
                    deletionStep = AccountDeletionStep.NONE,
                    errorMessage = "계정 삭제 요청을 처리하지 못했습니다.",
                )
            }
        }
        deletionInProgress = false
    }
}

class AccountViewModelFactory(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository?,
    private val deletionClient: AccountDeletionClient,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AccountViewModel::class.java))
        return AccountViewModel(
            authRepository = authRepository,
            profileRepository = profileRepository,
            deletionClient = deletionClient,
        ) as T
    }
}
