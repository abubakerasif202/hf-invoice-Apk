package com.hfremovalsadelaide.invoices.data

import android.webkit.CookieManager
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Communicates securely over HTTPS with the single-source-of-truth Next.js backend
 * (`https://hf-invoice-generator.vercel.app/api/manage`) using the authenticated first-party
 * session cookie stored in WebView's CookieManager. Cookies are never logged or persisted outside
 * WebView's CookieManager.
 */
class InvoiceApiRepository {

    companion object {
        const val MANAGE_ENDPOINT = "${TrustedOriginPolicy.PRODUCTION_URL}/api/manage"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 25_000

        /**
         * Parses the JSON response returned by GET /api/manage into structured domain models.
         */
        fun parseInvoicesPayload(jsonText: String): InvoicesSnapshot {
            val root = JSONObject(jsonText)

            val configObj = root.optJSONObject("config")
            val config = WorkspaceCompanyConfig(
                terms = configObj?.optString("terms", "").orEmpty(),
                defaultTax = configObj?.optString("defaultTax", "none")
                    ?.takeIf { it in setOf("none", "exclusive", "inclusive") } ?: "none",
                gstStatus = configObj?.optString("gst", "unconfirmed").orEmpty()
            )

            val customersArray = root.optJSONArray("customers") ?: JSONArray()
            val customers = buildList {
                for (i in 0 until customersArray.length()) {
                    val item = customersArray.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    val data = item.optJSONObject("data") ?: continue
                    val name = data.optString("name", "").trim()
                    if (id.isNotBlank() && name.isNotBlank()) {
                        add(
                            CustomerSummary(
                                id = id,
                                name = name,
                                address = data.optString("address", "").trim(),
                                email = data.optString("email", "").trim(),
                                phone = data.optString("phone", "").trim()
                            )
                        )
                    }
                }
            }.sortedBy { it.name.lowercase() }

            val recordsArray = root.optJSONArray("records") ?: JSONArray()
            val invoices = buildList {
                for (i in 0 until recordsArray.length()) {
                    val record = recordsArray.optJSONObject(i) ?: continue
                    val id = record.optString("id", "")
                    if (id.isBlank()) continue

                    val number = record.optString("number", "").ifBlank { "Draft" }
                    val status = InvoiceStatus.fromServerValue(record.optString("status"))
                    val buyerObj = record.optJSONObject("buyer") ?: record.optJSONObject("customer")
                    val dataObj = record.optJSONObject("data")
                    val totalsObj = record.optJSONObject("totals")

                    val customerName = buyerObj?.optString("name", "")?.trim()
                        ?.ifBlank { "Unnamed customer" } ?: "Unnamed customer"
                    val customerAddress = buyerObj?.optString("address", "")?.trim().orEmpty()
                    val issuedDate = dataObj?.optString("issued", "")?.trim().orEmpty()
                    val dueDate = dataObj?.optString("due", "")?.trim().orEmpty()
                    val description = extractServicesSummary(dataObj)

                    add(
                        InvoiceSummary(
                            id = id,
                            number = number,
                            customerName = customerName,
                            customerAddress = customerAddress,
                            issuedDate = issuedDate,
                            dueDate = dueDate,
                            status = status,
                            servicesDescription = description,
                            totalAmount = parseDecimal(totalsObj?.optString("total")),
                            balanceAmount = parseDecimal(totalsObj?.optString("balance")),
                            gstAmount = parseDecimal(totalsObj?.optString("gst")),
                            paidAmount = parseDecimal(totalsObj?.optString("paid"))
                        )
                    )
                }
            }.sortedWith(
                compareByDescending<InvoiceSummary> { it.issuedDate }
                    .thenByDescending { it.number }
            )

            return InvoicesSnapshot(
                invoices = invoices,
                customers = customers,
                config = config
            )
        }

        /**
         * Serializes a CreateInvoiceRequest into the exact JSON payload expected by
         * POST /api/manage (`action: "save"` or `action: "issue"`).
         */
        fun buildCreateInvoicePayload(
            request: CreateInvoiceRequest,
            config: WorkspaceCompanyConfig
        ): JSONObject {
            val formattedRate = request.totalAmount
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()

            val itemObj = JSONObject().apply {
                put("description", request.servicesPerformed.trim())
                put("quantity", "1")
                put("unit", "job")
                put("rate", formattedRate)
                put("tax", config.defaultTax.ifBlank { "none" })
                put("discount", false)
                put("note", "")
            }

            val invoiceData = JSONObject().apply {
                put("number", "")
                put("customerId", request.customerId.orEmpty())
                put("issued", request.issuedDate)
                put("due", request.dueDate)
                put("serviceFrom", request.issuedDate)
                put("serviceTo", request.issuedDate)
                put("job", "")
                put("pickup", request.customerAddress.trim())
                put("delivery", "")
                put("description", request.servicesPerformed.trim())
                put("terms", config.terms)
                put("notes", "")
                put("internal", "")
                put("days", JSONArray())
                put("items", JSONArray().put(itemObj))

                if (request.customerId.isNullOrBlank()) {
                    put(
                        "newCustomer",
                        JSONObject().apply {
                            put("kind", "Individual")
                            put("name", request.customerName.trim())
                            put("contact", "")
                            put("email", "")
                            put("phone", "")
                            put("address", request.customerAddress.trim())
                            put("abn", "")
                            put("notes", "")
                        }
                    )
                }
            }

            return JSONObject().apply {
                put("action", if (request.issueImmediately) "issue" else "save")
                put("id", "")
                put("data", invoiceData)
            }
        }

        private fun extractServicesSummary(dataObj: JSONObject?): String {
            if (dataObj == null) return ""
            val direct = dataObj.optString("description", "").trim()
            if (direct.isNotBlank()) return direct

            val items = dataObj.optJSONArray("items")
            if (items != null && items.length() > 0) {
                val itemDescriptions = buildList {
                    for (i in 0 until items.length()) {
                        val desc = items.optJSONObject(i)?.optString("description", "")?.trim()
                        if (!desc.isNullOrBlank()) add(desc)
                    }
                }
                if (itemDescriptions.isNotEmpty()) {
                    return itemDescriptions.joinToString(" · ")
                }
            }

            val days = dataObj.optJSONArray("days")
            if (days != null && days.length() > 0) {
                val count = days.length()
                return if (count == 1) "1 service day" else "$count service days"
            }

            return ""
        }

        private fun parseDecimal(raw: String?): BigDecimal {
            return runCatching {
                BigDecimal(raw?.trim().orEmpty())
            }.getOrDefault(BigDecimal.ZERO)
        }
    }

    suspend fun fetchInvoices(): InvoiceRepositoryResult<InvoicesSnapshot> =
        withContext(Dispatchers.IO) {
            if (!TrustedOriginPolicy.isTrustedOrigin(MANAGE_ENDPOINT)) {
                return@withContext InvoiceRepositoryResult.NetworkError
            }

            var connection: HttpsURLConnection? = null
            try {
                val cookie = CookieManager.getInstance().getCookie(MANAGE_ENDPOINT)
                if (cookie.isNullOrBlank()) {
                    return@withContext InvoiceRepositoryResult.AuthRequired
                }

                val rawConn = URL(MANAGE_ENDPOINT).openConnection()
                if (rawConn !is HttpsURLConnection) {
                    return@withContext InvoiceRepositoryResult.NetworkError
                }

                connection = rawConn.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cookie", cookie)
                }

                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_UNAUTHORIZED ||
                        code == HttpURLConnection.HTTP_FORBIDDEN -> {
                        InvoiceRepositoryResult.AuthRequired
                    }

                    code in 200..299 -> {
                        val body = readStreamText(connection.inputStream)
                        val snapshot = parseInvoicesPayload(body)
                        InvoiceRepositoryResult.Success(snapshot)
                    }

                    else -> {
                        val errBody = connection.errorStream?.let { readStreamText(it) }
                        val errMsg = extractErrorMessage(errBody)
                        InvoiceRepositoryResult.ServerError(errMsg)
                    }
                }
            } catch (_: Exception) {
                InvoiceRepositoryResult.NetworkError
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun createInvoice(
        request: CreateInvoiceRequest,
        config: WorkspaceCompanyConfig
    ): InvoiceRepositoryResult<String> = withContext(Dispatchers.IO) {
        if (!TrustedOriginPolicy.isTrustedOrigin(MANAGE_ENDPOINT)) {
            return@withContext InvoiceRepositoryResult.NetworkError
        }

        var connection: HttpsURLConnection? = null
        try {
            val cookie = CookieManager.getInstance().getCookie(MANAGE_ENDPOINT)
            if (cookie.isNullOrBlank()) {
                return@withContext InvoiceRepositoryResult.AuthRequired
            }

            val payloadBytes = buildCreateInvoicePayload(request, config)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            val rawConn = URL(MANAGE_ENDPOINT).openConnection()
            if (rawConn !is HttpsURLConnection) {
                return@withContext InvoiceRepositoryResult.NetworkError
            }

            connection = rawConn.apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cookie", cookie)
                // The backend rejects state-changing requests that lack its own Origin.
                setRequestProperty("Origin", TrustedOriginPolicy.PRODUCTION_URL)
                setFixedLengthStreamingMode(payloadBytes.size)
            }

            connection.outputStream.use { out ->
                out.write(payloadBytes)
                out.flush()
            }

            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    code == HttpURLConnection.HTTP_FORBIDDEN -> {
                    InvoiceRepositoryResult.AuthRequired
                }

                code in 200..299 -> {
                    val body = readStreamText(connection.inputStream)
                    val id = runCatching { JSONObject(body).optString("id", "") }.getOrDefault("")
                    InvoiceRepositoryResult.Success(id)
                }

                else -> {
                    val errBody = connection.errorStream?.let { readStreamText(it) }
                    val errMsg = extractErrorMessage(errBody)
                    InvoiceRepositoryResult.ServerError(errMsg)
                }
            }
        } catch (_: Exception) {
            InvoiceRepositoryResult.NetworkError
        } finally {
            connection?.disconnect()
        }
    }

    private fun readStreamText(stream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
    }

    private fun extractErrorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            JSONObject(raw).optString("error", "").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
