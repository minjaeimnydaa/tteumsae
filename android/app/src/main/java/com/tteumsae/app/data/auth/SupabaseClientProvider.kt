package com.tteumsae.app.data.auth

import com.tteumsae.app.BuildConfig
import com.tteumsae.app.domain.account.LoginProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Kakao
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object SupabaseClientProvider {
    fun createOrNull(
        enabled: Boolean = BuildConfig.AUTH_ENABLED,
        url: String = BuildConfig.SUPABASE_URL,
        publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    ): SupabaseClient? {
        if (!enabled || url.isBlank() || publishableKey.isBlank()) return null

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = publishableKey,
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "tteumsae"
                host = "auth-callback"
            }
            install(Postgrest)
        }
    }
}

@OptIn(SupabaseExperimental::class)
class SupabaseAuthGateway(
    private val client: SupabaseClient,
) : AuthGateway {
    private val sessionStatuses = client.auth.sessionStatus.map { status ->
        when (status) {
            SessionStatus.Initializing -> AuthGatewayStatus.Initializing
            is SessionStatus.NotAuthenticated -> AuthGatewayStatus.SignedOut
            is SessionStatus.RefreshFailure -> AuthGatewayStatus.RefreshFailed
            is SessionStatus.Authenticated -> {
                val provider = status.session.loginProvider()
                val userId = status.session.user?.id.orEmpty()
                if (userId.isBlank() || provider == null) {
                    AuthGatewayStatus.NeedsReauthentication
                } else {
                    AuthGatewayStatus.SignedIn(
                        userId = userId,
                        provider = provider,
                    )
                }
            }
        }
    }

    private val authEvents = client.auth.events.map { event ->
        when (event) {
            is AuthEvent.OtpError -> AuthGatewayStatus.LoginFailed
            is AuthEvent.RefreshFailure -> AuthGatewayStatus.RefreshFailed
        }
    }

    override val statuses: Flow<AuthGatewayStatus> = merge(sessionStatuses, authEvents)

    override suspend fun signIn(provider: LoginProvider): AuthStartResult {
        when (provider) {
            LoginProvider.KAKAO -> client.auth.signInWith(Kakao)
            LoginProvider.GOOGLE -> client.auth.signInWith(Google)
        }
        return AuthStartResult.Started
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun clearLocalSession() {
        client.auth.clearSession()
    }

    override fun accessToken(): String? = client.auth.currentAccessTokenOrNull()
}

private fun io.github.jan.supabase.auth.user.UserSession.loginProvider(): LoginProvider? {
    val currentUser = user ?: return null
    val metadataProvider = (currentUser.appMetadata?.get("provider") as? JsonPrimitive)
        ?.contentOrNull

    return metadataProvider.toLoginProvider()
        ?: currentUser.identities.orEmpty().firstNotNullOfOrNull { identity ->
            identity.provider.toLoginProvider()
        }
}

private fun String?.toLoginProvider(): LoginProvider? =
    when (this?.lowercase()) {
        "kakao" -> LoginProvider.KAKAO
        "google" -> LoginProvider.GOOGLE
        else -> null
    }
