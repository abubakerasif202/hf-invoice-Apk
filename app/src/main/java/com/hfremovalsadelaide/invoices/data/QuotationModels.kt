package com.hfremovalsadelaide.invoices.data

import com.hfremovalsadelaide.invoices.network.ApiClient
import com.hfremovalsadelaide.invoices.network.ApiResult
import com.hfremovalsadelaide.invoices.offline.EncryptedStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

data class TermsVersionRecord(
    val id: String,
    val segment: String?,
    val title: String,
    val body: String,
    val versionNumber: Int,
    val effectiveFrom: String,
    val effectiveTo: String? = null,
    val status: String = "approved",
    val supersedesId: String? = null
)

data class DuplicateCustomerMatch(
    val id: String,
    val name: String,
    val email: String,
    val phone: String
)

data class QuoteWizardStop(
    val id: String = UUID.randomUUID().toString(),
    val kind: String = "stop", // pickup | delivery | stop
    val address: String = "",
    val notes: String = ""
)

data class QuoteServiceDayDraft(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val rateCardId: String? = null,
    val rateUnit: String = "half_hour", // hourly | half_hour | per_day
    val truckClass: String = "",
    val manualRate: String = "",
    val manualRateReason: String = "",
    val movers: Int = 2,
    val estimatedHours: String = "3",
    val publicHoliday: Boolean = false,
    val callOut: Boolean = false,
    val travel: Boolean = false,
    val notes: String = "",
    val expanded: Boolean = true
)

data class QuoteSelectedServiceDraft(
    val catalogueId: String,
    val name: String,
    val unit: ServiceUnit,
    val quantity: String = "1"
)

data class QuoteWizardDraft(
    val stepIndex: Int = 0,
    val editingQuoteId: String? = null,
    val revisionReason: String = "",
    // Step 1: Customer
    val customerId: String? = null,
    val customerKind: String = "Individual",
    val customerName: String = "",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    // Step 2: Moving details
    val segment: String = "residential",
    val preferredDate: String = "",
    val extraDates: List<String> = emptyList(),
    val pickup: String = "",
    val delivery: String = "",
    val stops: List<QuoteWizardStop> = emptyList(),
    val propertyType: String = "",
    val bedrooms: String = "",
    val stairsAndLifts: String = "",
    val parkingAndAccess: String = "",
    val inventorySummary: String = "",
    val heavyItemsSummary: String = "",
    val specialInstructions: String = "",
    // Step 3: Truck & crew
    val pricingMode: String = "hourly_estimate", // hourly_estimate | fixed_price
    val fixedPrice: String = "",
    val serviceDays: List<QuoteServiceDayDraft> = emptyList(),
    // Step 4: Additional services & adjustments
    val selectedServices: List<QuoteSelectedServiceDraft> = emptyList(),
    val packingQuantity: String = "",
    val heavyItemCount: String = "",
    val materialsNotes: String = "",
    val discountEnabled: Boolean = false,
    val discountType: String = "percent", // percent | amount
    val discountValue: String = "",
    val discountReason: String = "",
    val depositReceived: String = "",
    // Step 5: Review & terms
    val termsVersionId: String? = null,
    val notes: String = "",
    val followUpDate: String = ""
) {
    fun allDates(): List<String> = buildList {
        if (preferredDate.isNotBlank()) add(preferredDate.trim())
        extraDates.filter { it.isNotBlank() && it.trim() != preferredDate.trim() }.forEach { add(it.trim()) }
    }.distinct()

    fun toJson(): JSONObject = JSONObject().apply {
        put("stepIndex", stepIndex)
        put("editingQuoteId", editingQuoteId ?: JSONObject.NULL)
        put("revisionReason", revisionReason)
        put("customerId", customerId ?: JSONObject.NULL)
        put("customerKind", customerKind)
        put("customerName", customerName)
        put("customerEmail", customerEmail)
        put("customerPhone", customerPhone)
        put("customerAddress", customerAddress)
        put("segment", segment)
        put("preferredDate", preferredDate)
        put("extraDates", JSONArray(extraDates))
        put("pickup", pickup)
        put("delivery", delivery)
        put("stops", JSONArray().apply {
            stops.forEach { s ->
                put(JSONObject().put("id", s.id).put("kind", s.kind).put("address", s.address).put("notes", s.notes))
            }
        })
        put("propertyType", propertyType)
        put("bedrooms", bedrooms)
        put("stairsAndLifts", stairsAndLifts)
        put("parkingAndAccess", parkingAndAccess)
        put("inventorySummary", inventorySummary)
        put("heavyItemsSummary", heavyItemsSummary)
        put("specialInstructions", specialInstructions)
        put("pricingMode", pricingMode)
        put("fixedPrice", fixedPrice)
        put("serviceDays", JSONArray().apply {
            serviceDays.forEach { d ->
                put(JSONObject()
                    .put("id", d.id)
                    .put("date", d.date)
                    .put("rateCardId", d.rateCardId ?: JSONObject.NULL)
                    .put("rateUnit", d.rateUnit)
                    .put("truckClass", d.truckClass)
                    .put("manualRate", d.manualRate)
                    .put("manualRateReason", d.manualRateReason)
                    .put("movers", d.movers)
                    .put("estimatedHours", d.estimatedHours)
                    .put("publicHoliday", d.publicHoliday)
                    .put("callOut", d.callOut)
                    .put("travel", d.travel)
                    .put("notes", d.notes)
                    .put("expanded", d.expanded))
            }
        })
        put("selectedServices", JSONArray().apply {
            selectedServices.forEach { sv ->
                put(JSONObject()
                    .put("catalogueId", sv.catalogueId)
                    .put("name", sv.name)
                    .put("unit", sv.unit.key)
                    .put("quantity", sv.quantity))
            }
        })
        put("packingQuantity", packingQuantity)
        put("heavyItemCount", heavyItemCount)
        put("materialsNotes", materialsNotes)
        put("discountEnabled", discountEnabled)
        put("discountType", discountType)
        put("discountValue", discountValue)
        put("discountReason", discountReason)
        put("depositReceived", depositReceived)
        put("termsVersionId", termsVersionId ?: JSONObject.NULL)
        put("notes", notes)
        put("followUpDate", followUpDate)
    }

    companion object {
        private fun JSONObject.str(k: String): String? =
            if (isNull(k)) null else optString(k, "").trim().takeIf { it.isNotEmpty() }

        fun fromJson(o: JSONObject): QuoteWizardDraft {
            val extraArr = o.optJSONArray("extraDates") ?: JSONArray()
            val stopsArr = o.optJSONArray("stops") ?: JSONArray()
            val daysArr = o.optJSONArray("serviceDays") ?: JSONArray()
            val svArr = o.optJSONArray("selectedServices") ?: JSONArray()
            return QuoteWizardDraft(
                stepIndex = o.optInt("stepIndex", 0).coerceIn(0, 4),
                editingQuoteId = o.str("editingQuoteId"),
                revisionReason = o.optString("revisionReason", ""),
                customerId = o.str("customerId"),
                customerKind = o.optString("customerKind", "Individual"),
                customerName = o.optString("customerName", ""),
                customerEmail = o.optString("customerEmail", ""),
                customerPhone = o.optString("customerPhone", ""),
                customerAddress = o.optString("customerAddress", ""),
                segment = o.optString("segment", "residential"),
                preferredDate = o.optString("preferredDate", ""),
                extraDates = List(extraArr.length()) { extraArr.optString(it) }.filter { it.isNotBlank() },
                pickup = o.optString("pickup", ""),
                delivery = o.optString("delivery", ""),
                stops = List(stopsArr.length()) { i ->
                    val s = stopsArr.getJSONObject(i)
                    QuoteWizardStop(
                        id = s.optString("id", UUID.randomUUID().toString()),
                        kind = s.optString("kind", "stop"),
                        address = s.optString("address", ""),
                        notes = s.optString("notes", "")
                    )
                },
                propertyType = o.optString("propertyType", ""),
                bedrooms = o.optString("bedrooms", ""),
                stairsAndLifts = o.optString("stairsAndLifts", ""),
                parkingAndAccess = o.optString("parkingAndAccess", ""),
                inventorySummary = o.optString("inventorySummary", ""),
                heavyItemsSummary = o.optString("heavyItemsSummary", ""),
                specialInstructions = o.optString("specialInstructions", ""),
                pricingMode = o.optString("pricingMode", "hourly_estimate"),
                fixedPrice = o.optString("fixedPrice", ""),
                serviceDays = List(daysArr.length()) { i ->
                    val d = daysArr.getJSONObject(i)
                    QuoteServiceDayDraft(
                        id = d.optString("id", UUID.randomUUID().toString()),
                        date = d.optString("date", ""),
                        rateCardId = d.str("rateCardId"),
                        rateUnit = d.optString("rateUnit", "half_hour"),
                        truckClass = d.optString("truckClass", ""),
                        manualRate = d.optString("manualRate", ""),
                        manualRateReason = d.optString("manualRateReason", ""),
                        movers = d.optInt("movers", 2),
                        estimatedHours = d.optString("estimatedHours", "3"),
                        publicHoliday = d.optBoolean("publicHoliday", false),
                        callOut = d.optBoolean("callOut", false),
                        travel = d.optBoolean("travel", false),
                        notes = d.optString("notes", ""),
                        expanded = d.optBoolean("expanded", true)
                    )
                },
                selectedServices = List(svArr.length()) { i ->
                    val sv = svArr.getJSONObject(i)
                    QuoteSelectedServiceDraft(
                        catalogueId = sv.getString("catalogueId"),
                        name = sv.optString("name", ""),
                        unit = ServiceUnit.fromKey(sv.optString("unit")),
                        quantity = sv.optString("quantity", "1")
                    )
                },
                packingQuantity = o.optString("packingQuantity", ""),
                heavyItemCount = o.optString("heavyItemCount", ""),
                materialsNotes = o.optString("materialsNotes", ""),
                discountEnabled = o.optBoolean("discountEnabled", false),
                discountType = o.optString("discountType", "percent"),
                discountValue = o.optString("discountValue", ""),
                discountReason = o.optString("discountReason", ""),
                depositReceived = o.optString("depositReceived", ""),
                termsVersionId = o.str("termsVersionId"),
                notes = o.optString("notes", ""),
                followUpDate = o.optString("followUpDate", "")
            )
        }
    }
}

data class QuotePriceLine(
    val kind: String,
    val description: String,
    val quantity: String,
    val unit: String,
    val rate: String,
    val amount: String,
    val gst: String
)

data class QuotePriceResult(
    val segment: String,
    val pricingMode: String,
    val isEstimate: Boolean,
    val lines: List<QuotePriceLine>,
    val subtotal: String,
    val discount: String,
    val gst: String,
    val total: String,
    val depositRequested: String?,
    val depositReceived: String,
    val balanceEstimated: String,
    val validUntil: String?,
    val quoteValidityDays: String?,
    val cancellationFee: String?,
    val issues: List<String>,
    val issuable: Boolean
)

data class QuoteRevisionRecord(
    val id: String,
    val quoteId: String,
    val revisionNumber: Int,
    val reason: String,
    val status: String,
    val segment: String,
    val validUntil: String?,
    val issuedBy: String,
    val issuedAt: String,
    val acceptedAt: String?,
    val acceptanceMethod: String?,
    val acceptedByStaff: String?,
    val termsTitle: String?,
    val termsVersionNumber: Int?,
    val total: String?,
    val isEstimate: Boolean,
    val isLegacy: Boolean
)

data class QuoteDetailPayload(
    val quoteId: String,
    val revisions: List<QuoteRevisionRecord>,
    val legacyStatus: String?,
    val platformReady: Boolean,
    val pricingRequestJson: JSONObject?,
    val draftDetailsJson: JSONObject?
)

/** Encrypted, account-isolated offline storage for unfinished native quotation wizard drafts. */
class QuoteDraftStore(private val store: EncryptedStore) {
    private fun keyFor(accountId: String) = "quote_wizard_draft_${accountId.ifBlank { "default" }}"

    fun saveDraft(accountId: String, draft: QuoteWizardDraft) {
        store.writeText(keyFor(accountId), draft.toJson().toString())
    }

    fun loadDraft(accountId: String): QuoteWizardDraft? {
        val raw = store.readText(keyFor(accountId)) ?: return null
        return runCatching { QuoteWizardDraft.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun clearDraft(accountId: String) {
        store.delete(keyFor(accountId))
    }
}

object QuotationRepository {
    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k, "").trim().takeIf { it.isNotEmpty() }

    internal fun parseTerms(jsonText: String): List<TermsVersionRecord> {
        val arr = JSONObject(jsonText).optJSONArray("terms") ?: JSONArray()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            TermsVersionRecord(
                id = o.getString("id"),
                segment = o.str("segment"),
                title = o.getString("title"),
                body = o.getString("body"),
                versionNumber = o.optInt("version_number", 1),
                effectiveFrom = o.getString("effective_from"),
                effectiveTo = o.str("effective_to"),
                status = o.optString("status", "approved"),
                supersedesId = o.str("supersedes_id")
            )
        }
    }

    internal fun parsePriceResult(jsonText: String): QuotePriceResult {
        val o = JSONObject(jsonText)
        val linesArr = o.optJSONArray("lines") ?: JSONArray()
        val lines = List(linesArr.length()) { i ->
            val l = linesArr.getJSONObject(i)
            QuotePriceLine(
                kind = l.optString("kind", "service"),
                description = l.optString("description", ""),
                quantity = l.optString("quantity", "1"),
                unit = l.optString("unit", ""),
                rate = l.optString("rate", "0.00"),
                amount = l.optString("amount", "0.00"),
                gst = l.optString("gst", "none")
            )
        }
        val issuesArr = o.optJSONArray("issues") ?: JSONArray()
        val issues = List(issuesArr.length()) { issuesArr.getString(it) }
        val termsObj = o.optJSONObject("terms")
        return QuotePriceResult(
            segment = o.getString("segment"),
            pricingMode = o.getString("pricingMode"),
            isEstimate = o.optBoolean("isEstimate", true),
            lines = lines,
            subtotal = o.getString("subtotal"),
            discount = o.optString("discount"),
            gst = o.getString("gst"),
            total = o.getString("total"),
            depositRequested = o.str("depositRequested"),
            depositReceived = o.optString("depositReceived", "0.00"),
            balanceEstimated = o.getString("balanceEstimated"),
            validUntil = o.str("validUntil"),
            quoteValidityDays = termsObj?.str("quoteValidityDays"),
            cancellationFee = termsObj?.str("cancellationFee"),
            issues = issues,
            issuable = o.optBoolean("issuable", false)
        )
    }

    internal fun buildPriceRequestPayload(draft: QuoteWizardDraft, fallbackDate: String): JSONObject {
        val daysArr = JSONArray()
        val days = if (draft.pricingMode == "hourly_estimate" && draft.serviceDays.isEmpty() && draft.preferredDate.isNotBlank()) {
            listOf(QuoteServiceDayDraft(date = draft.preferredDate.trim()))
        } else {
            draft.serviceDays
        }
        days.forEach { d ->
            val useManual = d.rateCardId == null && d.manualRate.isNotBlank()
            val cardId = d.rateCardId?.takeIf { !useManual && it.isNotBlank() }
            daysArr.put(
                JSONObject()
                    .put("date", d.date.ifBlank { fallbackDate })
                    .put("rateCardId", cardId ?: JSONObject.NULL)
                    .put("rateUnit", d.rateUnit)
                    .put("truckClass", d.truckClass.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    .put("manualRate", d.manualRate.takeIf { useManual } ?: JSONObject.NULL)
                    .put("manualRateReason", d.manualRateReason.takeIf { useManual && it.isNotBlank() } ?: JSONObject.NULL)
                    .put("movers", d.movers.coerceIn(1, 20))
                    .put("estimatedHours", d.estimatedHours.takeIf { d.rateUnit != "per_day" && it.isNotBlank() } ?: JSONObject.NULL)
                    .put("publicHoliday", d.publicHoliday)
                    .put("callOut", d.callOut)
                    .put("travel", d.travel)
            )
        }
        val servicesArr = JSONArray().apply {
            draft.selectedServices.forEach { sv ->
                put(JSONObject().put("catalogueId", sv.catalogueId).put("quantity", sv.quantity.trim().ifBlank { "1" }))
            }
        }
        return JSONObject().apply {
            put("segment", draft.segment)
            put("pricingMode", draft.pricingMode)
            put("serviceDays", daysArr)
            put("fixedPrice", draft.fixedPrice.trim().takeIf { draft.pricingMode == "fixed_price" && it.isNotBlank() } ?: JSONObject.NULL)
            put("services", servicesArr)
            put("packingQuantity", draft.packingQuantity.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("heavyItemCount", draft.heavyItemCount.trim().toIntOrNull() ?: JSONObject.NULL)
            if (draft.discountEnabled && draft.discountValue.trim().isNotBlank()) {
                put(
                    "discount",
                    JSONObject()
                        .put("type", draft.discountType)
                        .put("value", draft.discountValue.trim())
                        .put("reason", draft.discountReason.trim())
                )
            } else {
                put("discount", JSONObject.NULL)
            }
            put("depositReceived", draft.depositReceived.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        }
    }

    internal fun buildSaveWizardQuotePayload(draft: QuoteWizardDraft, fallbackDate: String): JSONObject {
        val primaryDay = draft.serviceDays.firstOrNull()
        val draftDetails = JSONObject()
            .put("preferredDates", JSONArray(draft.allDates()))
            .put("stops", JSONArray().apply {
                draft.stops.filter { it.address.isNotBlank() }.forEach { s ->
                    put(JSONObject().put("kind", s.kind).put("address", s.address.trim()).put("notes", s.notes.trim()))
                }
            })
            .put("propertyType", draft.propertyType.trim())
            .put("bedrooms", draft.bedrooms.trim())
            .put("stairsAndLifts", draft.stairsAndLifts.trim())
            .put("parkingAndAccess", draft.parkingAndAccess.trim())
            .put("inventorySummary", draft.inventorySummary.trim())
            .put("heavyItemsSummary", draft.heavyItemsSummary.trim())
            .put("specialInstructions", draft.specialInstructions.trim())
            .put("materialsNotes", draft.materialsNotes.trim())
            .put("serviceDaysMeta", JSONArray().apply {
                draft.serviceDays.forEach { d ->
                    put(
                        JSONObject()
                            .put("date", d.date.ifBlank { fallbackDate })
                            .put("truckLabel", d.truckClass.trim())
                            .put("movers", d.movers)
                            .put("notes", d.notes.trim())
                    )
                }
            })

        return JSONObject().apply {
            put("action", "save_quote")
            put("id", draft.editingQuoteId ?: JSONObject.NULL)
            put("customerId", draft.customerId ?: JSONObject.NULL)
            if (draft.customerId == null && draft.customerName.isNotBlank()) {
                put(
                    "newCustomer",
                    JSONObject()
                        .put("kind", draft.customerKind)
                        .put("name", draft.customerName.trim())
                        .put("contact", "")
                        .put("email", draft.customerEmail.trim())
                        .put("phone", draft.customerPhone.trim())
                        .put("address", draft.customerAddress.trim())
                        .put("abn", "")
                        .put("notes", "")
                )
            }
            put("stage", "quote_prepared")
            put("segment", draft.segment)
            put("pricing", buildPriceRequestPayload(draft, fallbackDate))
            put("preferredDate", draft.preferredDate.trim().takeIf { it.isNotBlank() } ?: primaryDay?.date?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("pickup", draft.pickup.trim())
            put("delivery", draft.delivery.trim())
            put("truckClass", primaryDay?.truckClass?.trim().orEmpty())
            put("movers", primaryDay?.movers ?: 2)
            put("estimatedHours", primaryDay?.estimatedHours?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("notes", draft.notes.trim())
            put("followUpDate", draft.followUpDate.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("draftDetails", draftDetails)
            put("termsVersionId", draft.termsVersionId ?: JSONObject.NULL)
        }
    }

    internal fun parseQuoteDetail(jsonText: String): QuoteDetailPayload {
        val root = JSONObject(jsonText)
        val quote = root.getJSONObject("quote")
        val revsArr = root.optJSONArray("revisions") ?: JSONArray()
        val revisions = List(revsArr.length()) { i ->
            val r = revsArr.getJSONObject(i)
            val snap = r.optJSONObject("pricing_snapshot")
            val calc = snap?.optJSONObject("calculation")
            val termsSnap = r.optJSONObject("terms_snapshot")
            QuoteRevisionRecord(
                id = r.getString("id"),
                quoteId = r.getString("quote_id"),
                revisionNumber = r.getInt("revision_number"),
                reason = r.optString("reason", ""),
                status = r.getString("status"),
                segment = r.optString("segment", "residential"),
                validUntil = r.str("valid_until"),
                issuedBy = r.optString("issued_by", ""),
                issuedAt = r.optString("issued_at", ""),
                acceptedAt = r.str("accepted_at"),
                acceptanceMethod = r.str("acceptance_method"),
                acceptedByStaff = r.str("accepted_by_staff"),
                termsTitle = termsSnap?.str("title"),
                termsVersionNumber = termsSnap?.optInt("versionNumber"),
                total = calc?.str("total"),
                isEstimate = calc?.optBoolean("isEstimate", true) ?: true,
                isLegacy = r.optBoolean("is_legacy", false)
            )
        }
        return QuoteDetailPayload(
            quoteId = quote.getString("id"),
            revisions = revisions,
            legacyStatus = root.str("legacyStatus"),
            platformReady = root.optBoolean("platformReady", false),
            pricingRequestJson = quote.optJSONObject("pricing_calculation")?.optJSONObject("request"),
            draftDetailsJson = quote.optJSONObject("draft_details")
        )
    }

    suspend fun fetchTerms(): ApiResult<List<TermsVersionRecord>> =
        ApiClient.get("/api/quotes/terms", ::parseTerms)

    suspend fun saveTermsDraft(
        segment: String?,
        title: String,
        body: String,
        effectiveFrom: String,
        supersedesId: String? = null
    ): ApiResult<String> = ApiClient.post(
        "/api/quotes/terms",
        JSONObject()
            .put("action", "save")
            .put("segment", segment ?: JSONObject.NULL)
            .put("title", title.trim())
            .put("body", body.trim())
            .put("effectiveFrom", effectiveFrom.trim())
            .put("supersedesId", supersedesId ?: JSONObject.NULL)
    ) { JSONObject(it).getString("id") }

    suspend fun approveTerms(id: String): ApiResult<Boolean> =
        ApiClient.post("/api/quotes/terms", JSONObject().put("action", "approve").put("id", id)) { true }

    suspend fun checkDuplicateCustomers(name: String, email: String, phone: String): ApiResult<List<DuplicateCustomerMatch>> {
        val q = listOf(
            "checkName=${URLEncoder.encode(name.trim(), "UTF-8")}",
            "checkEmail=${URLEncoder.encode(email.trim(), "UTF-8")}",
            "checkPhone=${URLEncoder.encode(phone.trim(), "UTF-8")}"
        ).joinToString("&")
        return ApiClient.get("/api/crm?$q") { text ->
            val arr = JSONObject(text).optJSONArray("duplicates") ?: JSONArray()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DuplicateCustomerMatch(
                    id = o.getString("id"),
                    name = o.optString("name", ""),
                    email = o.optString("email", ""),
                    phone = o.optString("phone", "")
                )
            }
        }
    }

    suspend fun priceQuote(draft: QuoteWizardDraft, fallbackDate: String): ApiResult<QuotePriceResult> =
        ApiClient.post("/api/quotes/price", buildPriceRequestPayload(draft, fallbackDate), ::parsePriceResult)

    suspend fun saveWizardQuote(draft: QuoteWizardDraft, fallbackDate: String): ApiResult<String> =
        ApiClient.post("/api/crm", buildSaveWizardQuotePayload(draft, fallbackDate)) { JSONObject(it).getString("id") }

    suspend fun fetchQuoteDetail(quoteId: String): ApiResult<QuoteDetailPayload> =
        ApiClient.get("/api/quotes/$quoteId", ::parseQuoteDetail)

    suspend fun issueQuoteRevision(quoteId: String, reason: String, termsVersionId: String?): ApiResult<String> =
        ApiClient.post(
            "/api/quotes/$quoteId",
            JSONObject()
                .put("action", "issue_revision")
                .put("reason", reason.trim())
                .put("termsVersionId", termsVersionId ?: JSONObject.NULL)
        ) { JSONObject(it).getJSONObject("revision").getString("id") }

    suspend fun createRevisionDraft(quoteId: String, reason: String): ApiResult<Boolean> =
        ApiClient.post(
            "/api/quotes/$quoteId",
            JSONObject().put("action", "create_revision_draft").put("reason", reason.trim())
        ) { true }

    suspend fun cloneVariation(quoteId: String, reason: String): ApiResult<String> =
        ApiClient.post(
            "/api/quotes/$quoteId",
            JSONObject().put("action", "clone_variation").put("reason", reason.trim())
        ) { JSONObject(it).getString("id") }

    suspend fun acceptQuoteRevision(quoteId: String, revisionId: String, method: String, notes: String): ApiResult<Boolean> =
        ApiClient.post(
            "/api/quotes/$quoteId",
            JSONObject()
                .put("action", "accept_revision")
                .put("revisionId", revisionId)
                .put("method", method)
                .put("notes", notes.trim())
        ) { true }

    suspend fun convertQuoteToBooking(quoteId: String, overrideReason: String? = null): ApiResult<Pair<String, Boolean>> =
        ApiClient.post(
            "/api/quotes/$quoteId",
            JSONObject()
                .put("action", "convert_to_booking")
                .put("overrideReason", overrideReason?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        ) { text ->
            val o = JSONObject(text)
            o.getString("jobId") to o.optBoolean("alreadyConverted", false)
        }
}
