package com.hfremovalsadelaide.invoices.network

import android.webkit.CookieManager
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data object AuthRequired : ApiResult<Nothing>
    data class Forbidden(val message: String) : ApiResult<Nothing>
    data class NetworkError(val message: String = "No connection. Check your internet and try again.") : ApiResult<Nothing>
    data class ServerError(val code: Int, val message: String) : ApiResult<Nothing>
}

object ApiClient {
    const val BASE_URL = TrustedOriginPolicy.PRODUCTION_URL
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 25_000

    suspend fun <T> get(
        path: String,
        parse: (String) -> T
    ): ApiResult<T> = request("GET", path, null, parse)

    suspend fun <T> post(
        path: String,
        body: JSONObject,
        parse: (String) -> T
    ): ApiResult<T> = request("POST", path, body.toString(), parse)

    suspend fun <T> request(
        method: String,
        path: String,
        bodyJson: String?,
        parse: (String) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val endpoint = BASE_URL + path
        if (!TrustedOriginPolicy.isTrustedOrigin(endpoint)) {
            return@withContext ApiResult.Forbidden("Untrusted endpoint.")
        }
        var connection: HttpsURLConnection? = null
        try {
            val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
            val cookieHeader = cookieManager?.getCookie(endpoint)
            val raw = URL(endpoint).openConnection() as? HttpsURLConnection
                ?: return@withContext ApiResult.NetworkError()
            connection = raw.apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "application/json")
                if (!cookieHeader.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
                if (bodyJson != null) {
                    val bytes = bodyJson.toByteArray(StandardCharsets.UTF_8)
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Origin", BASE_URL)
                    setFixedLengthStreamingMode(bytes.size)
                    outputStream.use { it.write(bytes) }
                }
            }

            val code = connection.responseCode
            // Sync Set-Cookie headers into WebView's CookieManager so native & WebView auth share one session.
            val setCookies = connection.headerFields?.entries
                ?.firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
                ?.value
            if (!setCookies.isNullOrEmpty() && cookieManager != null) {
                setCookies.forEach { cookieManager.setCookie(BASE_URL, it) }
                cookieManager.flush()
            }

            when {
                code == HttpURLConnection.HTTP_UNAUTHORIZED -> ApiResult.AuthRequired
                code == HttpURLConnection.HTTP_FORBIDDEN -> {
                    val err = readError(connection) ?: "You do not have permission for this action."
                    ApiResult.Forbidden(err)
                }
                code in 200..299 -> {
                    val text = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    ApiResult.Success(parse(text))
                }
                else -> {
                    val err = readError(connection) ?: "Request failed ($code)."
                    ApiResult.ServerError(code, err)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ApiResult.NetworkError()
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun clearSessionCookies() = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val cm = CookieManager.getInstance()
            cm.removeAllCookies {
                cm.flush()
                continuation.resume(Unit)
            }
        }
    }

    private fun readError(connection: HttpsURLConnection): String? {
        val raw = runCatching {
            connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
        }.getOrNull() ?: return null
        return runCatching { JSONObject(raw).optString("error", "").takeIf { it.isNotBlank() } }.getOrNull()
    }
}
