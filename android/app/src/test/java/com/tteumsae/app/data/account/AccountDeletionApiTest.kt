package com.tteumsae.app.data.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionApiTest {
    @Test
    fun sends_delete_with_bearer_only_and_no_user_id_or_body() = runBlocking {
        val executor = FakeDeleteExecutor(204)
        val api = AccountDeletionApi(
            baseUrl = "https://api.example.com",
            executor = executor,
        )

        val result = api.deleteCurrentAccount("access-token")

        assertEquals(DeleteAccountResult.Success, result)
        assertEquals("DELETE", executor.request?.method)
        assertEquals("https://api.example.com/api/account", executor.request?.url)
        assertEquals("Bearer access-token", executor.request?.authorization)
        assertNull(executor.request?.body)
        assertTrue(executor.request.toString().contains("userId").not())
    }

    @Test
    fun maps_auth_rate_limit_and_server_statuses() = runBlocking {
        assertEquals(DeleteAccountResult.NeedsLogin, api(401).deleteCurrentAccount("token"))
        assertEquals(DeleteAccountResult.Retryable, api(429).deleteCurrentAccount("token"))
        assertEquals(DeleteAccountResult.Retryable, api(503).deleteCurrentAccount("token"))
        assertEquals(DeleteAccountResult.Failed, api(400).deleteCurrentAccount("token"))
    }

    @Test
    fun blank_token_is_rejected_without_a_network_request() = runBlocking {
        val executor = FakeDeleteExecutor(204)
        val result = AccountDeletionApi("https://api.example.com", executor)
            .deleteCurrentAccount("  ")

        assertEquals(DeleteAccountResult.NeedsLogin, result)
        assertNull(executor.request)
    }

    private fun api(status: Int) = AccountDeletionApi(
        baseUrl = "https://api.example.com/",
        executor = FakeDeleteExecutor(status),
    )
}

private class FakeDeleteExecutor(
    private val status: Int,
) : AccountDeleteExecutor {
    var request: AccountDeleteRequest? = null

    override fun execute(request: AccountDeleteRequest): Int {
        this.request = request
        return status
    }
}
