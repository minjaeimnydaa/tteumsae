package com.tteumsae.app.data.auth

import android.content.Intent
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

interface AuthDeepLinkGateway {
    fun forward(rawUri: String, onError: () -> Unit)
}

sealed interface DeepLinkResult {
    data object Forwarded : DeepLinkResult

    data object Ignored : DeepLinkResult

    data object MissingCode : DeepLinkResult

    data object AuthUnavailable : DeepLinkResult
}

class AuthDeepLinkHandler(
    private val gateway: AuthDeepLinkGateway?,
    private val onFailure: () -> Unit = {},
) {
    fun handle(intent: Intent): DeepLinkResult = handle(intent.dataString)

    fun handle(rawUri: String?): DeepLinkResult {
        val parsed = rawUri?.toCallbackUriOrNull() ?: return DeepLinkResult.Ignored
        if (!parsed.scheme.equals(CALLBACK_SCHEME, ignoreCase = true)) {
            return DeepLinkResult.Ignored
        }
        if (!parsed.host.equals(CALLBACK_HOST, ignoreCase = true)) {
            return DeepLinkResult.Ignored
        }
        if (parsed.queryValue("code").isNullOrBlank()) {
            onFailure()
            return DeepLinkResult.MissingCode
        }

        val activeGateway = gateway ?: run {
            onFailure()
            return DeepLinkResult.AuthUnavailable
        }
        activeGateway.forward(rawUri, onFailure)
        return DeepLinkResult.Forwarded
    }

    private companion object {
        const val CALLBACK_SCHEME = "tteumsae"
        const val CALLBACK_HOST = "auth-callback"
    }
}

class SupabaseAuthDeepLinkGateway(
    private val client: SupabaseClient,
) : AuthDeepLinkGateway {
    override fun forward(rawUri: String, onError: () -> Unit) {
        val callbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUri))
        client.handleDeeplinks(
            intent = callbackIntent,
            onError = { onError() },
        )
    }
}

private fun String.toCallbackUriOrNull(): URI? =
    runCatching { URI(this) }.getOrNull()

private fun URI.queryValue(name: String): String? = rawQuery
    ?.split('&')
    ?.asSequence()
    ?.map { item -> item.substringBefore('=') to item.substringAfter('=', "") }
    ?.firstOrNull { (key, _) -> decodeQueryPart(key) == name }
    ?.second
    ?.let(::decodeQueryPart)

private fun decodeQueryPart(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault("")
