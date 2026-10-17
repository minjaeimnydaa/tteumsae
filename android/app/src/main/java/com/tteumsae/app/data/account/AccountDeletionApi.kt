package com.tteumsae.app.data.account

import com.tteumsae.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AccountDeletionClient {
    suspend fun deleteCurrentAccount(accessToken: String): DeleteAccountResult
}

sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult

    data object NeedsLogin : DeleteAccountResult

    data object Retryable : DeleteAccountResult

    data object Failed : DeleteAccountResult
}

data class AccountDeleteRequest(
    val method: String,
    val url: String,
    val authorization: String,
    val body: String? = null,
)

fun interface AccountDeleteExecutor {
    fun execute(request: AccountDeleteRequest): Int
}

class AccountDeletionApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val executor: AccountDeleteExecutor = HttpUrlConnectionAccountDeleteExecutor(),
) : AccountDeletionClient {
    override suspend fun deleteCurrentAccount(accessToken: String): DeleteAccountResult {
        val token = accessToken.trim()
        if (token.isEmpty()) return DeleteAccountResult.NeedsLogin

        return withContext(Dispatchers.IO) {
            val request = AccountDeleteRequest(
                method = "DELETE",
                url = "${baseUrl.trimEnd('/')}/api/account",
                authorization = "Bearer $token",
            )
            val status = try {
                executor.execute(request)
            } catch (_: IOException) {
                return@withContext DeleteAccountResult.Retryable
            }
            when {
                status == 204 -> DeleteAccountResult.Success
                status == 401 -> DeleteAccountResult.NeedsLogin
                status == 429 || status >= 500 -> DeleteAccountResult.Retryable
                else -> DeleteAccountResult.Failed
            }
        }
    }
}

private class HttpUrlConnectionAccountDeleteExecutor : AccountDeleteExecutor {
    override fun execute(request: AccountDeleteRequest): Int {
        val connection = URI.create(request.url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = request.method
            connection.connectTimeout = 12_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", request.authorization)
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
