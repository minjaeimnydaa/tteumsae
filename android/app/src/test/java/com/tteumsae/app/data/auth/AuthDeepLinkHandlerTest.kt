package com.tteumsae.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthDeepLinkHandlerTest {
    @Test
    fun exact_callback_with_a_code_is_forwarded_once() {
        val gateway = FakeDeepLinkGateway()
        val handler = AuthDeepLinkHandler(gateway = gateway)
        val uri = "tteumsae://auth-callback?code=valid-code"

        val result = handler.handle(uri)

        assertEquals(DeepLinkResult.Forwarded, result)
        assertEquals(listOf(uri), gateway.forwardedUris)
    }

    @Test
    fun wrong_scheme_is_ignored_before_the_sdk_adapter() {
        val gateway = FakeDeepLinkGateway()
        val handler = AuthDeepLinkHandler(gateway = gateway)

        val result = handler.handle("https://auth-callback?code=valid-code")

        assertEquals(DeepLinkResult.Ignored, result)
        assertEquals(emptyList<String>(), gateway.forwardedUris)
    }

    @Test
    fun wrong_host_is_ignored_before_the_sdk_adapter() {
        val gateway = FakeDeepLinkGateway()
        val handler = AuthDeepLinkHandler(gateway = gateway)

        val result = handler.handle("tteumsae://other-host?code=valid-code")

        assertEquals(DeepLinkResult.Ignored, result)
        assertEquals(emptyList<String>(), gateway.forwardedUris)
    }

    @Test
    fun callback_without_a_code_is_rejected_and_reported_safely() {
        val gateway = FakeDeepLinkGateway()
        var failures = 0
        val handler = AuthDeepLinkHandler(
            gateway = gateway,
            onFailure = { failures += 1 },
        )

        val result = handler.handle("tteumsae://auth-callback?error=access_denied")

        assertEquals(DeepLinkResult.MissingCode, result)
        assertEquals(emptyList<String>(), gateway.forwardedUris)
        assertEquals(1, failures)
    }

    @Test
    fun malformed_or_empty_uri_is_ignored() {
        val gateway = FakeDeepLinkGateway()
        val handler = AuthDeepLinkHandler(gateway = gateway)

        assertEquals(DeepLinkResult.Ignored, handler.handle(null))
        assertEquals(DeepLinkResult.Ignored, handler.handle("not a uri"))
        assertEquals(emptyList<String>(), gateway.forwardedUris)
    }

    @Test
    fun sdk_verification_error_is_reduced_to_the_safe_failure_callback() {
        val gateway = FakeDeepLinkGateway(failWhileForwarding = true)
        var failures = 0
        val handler = AuthDeepLinkHandler(
            gateway = gateway,
            onFailure = { failures += 1 },
        )

        val result = handler.handle("tteumsae://auth-callback?code=invalid")

        assertEquals(DeepLinkResult.Forwarded, result)
        assertEquals(1, failures)
    }
}

private class FakeDeepLinkGateway(
    private val failWhileForwarding: Boolean = false,
) : AuthDeepLinkGateway {
    val forwardedUris = mutableListOf<String>()

    override fun forward(rawUri: String, onError: () -> Unit) {
        forwardedUris += rawUri
        if (failWhileForwarding) onError()
    }
}
