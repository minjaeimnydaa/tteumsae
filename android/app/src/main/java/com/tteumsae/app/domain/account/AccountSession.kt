package com.tteumsae.app.domain.account

sealed interface AccountSession {
    data object Guest : AccountSession

    data object Restoring : AccountSession

    data class SignedIn(
        val userId: String,
        val provider: LoginProvider,
    ) : AccountSession

    data object NeedsReauthentication : AccountSession

    data class AuthUnavailable(
        val message: String,
    ) : AccountSession
}

enum class LoginProvider {
    KAKAO,
    GOOGLE,
}
