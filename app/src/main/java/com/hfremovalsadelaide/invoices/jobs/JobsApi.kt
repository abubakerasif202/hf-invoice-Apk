package com.hfremovalsadelaide.invoices.jobs

import android.webkit.CookieManager
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

object JobsApi : JobsGateway {
    private const val BASE = TrustedOriginPolicy.PRODUCTION_URL
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 25_000

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() }

    private fun instant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it, "") }.filter { it.isNotBlank() }

    fun parseJob(o: JSONObject): Job {
        val details = o.optJSONObject("details") ?: JSONObject()
        val actual = o.optJSONObject("actual") ?: JSONObject()
        return Job(
            id = o.getString("id"),
            title = o.optString("title", ""),
            scheduledStart = instant(o.str("scheduledStart")),
            scheduledEnd = instant(o.str("scheduledEnd")),
            dateOnly = o.optBoolean("dateOnly", false),
            serviceDate = o.str("serviceDate"),
            bookingStatus = BookingStatus.from(o.str("bookingStatus")),
            cancelled = o.optBoolean("cancelled", false),
            state = JobState.from(o.str("state")),
            signoff = SignoffStatus.from(o.str("signoffStatus")),
            reference = details.str("reference"),
            customerName = details.str("customerName"),
            customerPhone = details.str("customerPhone"),
            pickup = details.str("pickup"),
            destination = details.str("destination"),
            truck = o.str("truck") ?: details.str("truck"),
            crewSize = if (details.isNull("crewSize")) null else details.optInt("crewSize").takeIf { it > 0 },
            notes = details.str("notes"),
            assignees = o.optJSONArray("assignees").strings(),
            actualStart = instant(actual.str("start")),
            actualFinish = instant(actual.str("finish")),
            elapsedMinutes = actual.optInt("elapsedMinutes", 0),
            breakMinutes = actual.optInt("breakMinutes", 0),
            workingMinutes = actual.optInt("workingMinutes", 0)
        )
    }

    fun parseSnapshot(text: String): JobsSnapshot {
        val root = JSONObject(text)
        val jobsArray = root.optJSONArray("jobs") ?: JSONArray()
        val jobs = List(jobsArray.length()) { parseJob(jobsArray.getJSONObject(it)) }
        val summary = root.optJSONObject("summary") ?: JSONObject()
        val conflicts = summary.optJSONArray("conflicts")?.let { arr ->
            List(arr.length()) { arr.optJSONArray(it) }.mapNotNull { p -> p?.let { p.optString(0) to p.optString(1) } }
        } ?: emptyList()
        val calendar = root.optJSONObject("calendar") ?: JSONObject()
        return JobsSnapshot(
            jobs = jobs,
            todayConfirmed = summary.optJSONArray("todayConfirmed").strings().toSet(),
            upcoming = summary.optJSONArray("upcoming").strings().toSet(),
            inProgress = summary.optJSONArray("inProgress").strings().toSet(),
            completed = summary.optJSONArray("completed").strings().toSet(),
            awaitingSignoff = summary.optJSONArray("awaitingSignoff").strings().toSet(),
            unconfirmed = summary.optJSONArray("unconfirmed").strings().toSet(),
            conflicts = conflicts,
            calendarConnected = calendar.optBoolean("connected", false),
            lastSyncedAt = calendar.str("lastSyncedAt"),
            lastError = calendar.str("lastError")
        )
    }

    override suspend fun snapshot(): JobsResult<JobsSnapshot> = call("GET", "/api/jobs", null) { JobsResult.Success(parseSnapshot(it)) }
    override suspend fun detail(id: String): JobsResult<Job> = call("GET", "/api/jobs/$id", null) { JobsResult.Success(parseJob(JSONObject(it))) }
    override suspend fun sync(): JobsResult<String> = call("POST", "/api/jobs", JSONObject().put("action", "sync")) { JobsResult.Success(it) }
    override suspend fun act(id: String, action: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", action)) { JobsResult.Success(it) }

    override suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", "correct").put("field", field).put("at", atIso).put("reason", reason)) { JobsResult.Success(it) }

    override suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", "signoff").put("outcome", "signed").put("customerName", customerName).put("signaturePngBase64", pngBase64)) { JobsResult.Success(it) }

    override suspend fun skipSignoff(id: String, reason: String): JobsResult<String> =
        call("POST", "/api/jobs/$id", JSONObject().put("action", "signoff").put("outcome", "skipped").put("reason", reason)) { JobsResult.Success(it) }

    override suspend fun invoiceDraft(id: String): JobsResult<JSONObject> =
        call("GET", "/api/jobs/$id/invoice-draft", null) { JobsResult.Success(JSONObject(it)) }

    override suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>> = call("GET", "/api/jobs/notifications", null) { text ->
        val prefs = JSONObject(text).optJSONObject("preferences") ?: JSONObject()
        JobsResult.Success(NotificationCategory.entries.associateWith { prefs.optBoolean(it.serverKey, true) })
    }

    override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String> {
        val body = JSONObject().put("action", "preferences").put("preferences", JSONObject().apply { prefs.forEach { (k, v) -> put(k.serverKey, v) } })
        return call("POST", "/api/jobs/notifications", body) { JobsResult.Success(it) }
    }

    /** Saves an already-reviewed draft through the existing invoice endpoint. Never issues. */
    override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?): JobsResult<String> {
        val data = JSONObject(input.toString())
        if (newCustomer != null) data.put("newCustomer", newCustomer)
        data.put("customerId", "")
        return call("POST", "/api/manage", JSONObject().put("action", "save").put("id", "").put("data", data)) { JobsResult.Success(it) }
    }

    private suspend fun <T> call(method: String, path: String, body: JSONObject?, parse: (String) -> JobsResult<T>): JobsResult<T> =
        withContext(Dispatchers.IO) {
            val endpoint = BASE + path
            if (!TrustedOriginPolicy.isTrustedOrigin(endpoint)) return@withContext JobsResult.Failure("Untrusted endpoint.")
            var connection: HttpsURLConnection? = null
            try {
                val cookie = CookieManager.getInstance().getCookie(endpoint)
                if (cookie.isNullOrBlank()) return@withContext JobsResult.AuthRequired
                val raw = URL(endpoint).openConnection() as? HttpsURLConnection ?: return@withContext JobsResult.Failure("Network error.")
                connection = raw.apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false // the session cookie must only go to the verified host
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cookie", cookie)
                    if (body != null) {
                        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Origin", BASE) // the backend rejects state changes without its own origin
                        setFixedLengthStreamingMode(bytes.size)
                        outputStream.use { it.write(bytes) }
                    }
                }
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN && body == null -> JobsResult.AuthRequired
                    code in 200..299 -> parse(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
                    else -> {
                        val err = connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                        JobsResult.Failure(runCatching { JSONObject(err.orEmpty()).optString("error") }.getOrNull()?.ifBlank { null } ?: "Request failed ($code).")
                    }
                }
            } catch (_: Exception) {
                JobsResult.Failure("No connection. Check your internet and try again.")
            } finally {
                connection?.disconnect()
            }
        }
}
