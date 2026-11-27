package com.hfremovalsadelaide.invoices.data

import com.hfremovalsadelaide.invoices.network.ApiClient
import com.hfremovalsadelaide.invoices.network.ApiResult
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

enum class PricingSegment(val key: String, val label: String) {
    RESIDENTIAL("residential", "Residential"),
    COMMERCIAL("commercial", "Commercial"),
    INTERSTATE("interstate", "Interstate");

    companion object {
        fun fromKey(raw: String?): PricingSegment? = entries.firstOrNull { it.key == raw }
    }
}

enum class PolicyCategory(val label: String) {
    GST_AND_QUOTING("GST & Quoting"),
    MINIMUMS_AND_INCREMENTS("Minimums & Increments"),
    SURCHARGES_AND_OVERTIME("Surcharges & Overtime"),
    CALL_OUT_AND_TRAVEL("Call-Out & Travel"),
    DEPOSITS_AND_VALIDITY("Deposits & Validity"),
    ADDITIONAL_SERVICES("Additional Services")
}

enum class PolicyValueKind { BOOLEAN, ENUM, DECIMAL, INTEGER, MONEY }

data class PolicyOption(val value: String, val label: String)

enum class PricingPolicyKey(
    val key: String,
    val label: String,
    val category: PolicyCategory,
    val kind: PolicyValueKind,
    val unitLabel: String?,
    val hint: String,
    val options: List<PolicyOption> = emptyList()
) {
    RATES_INCLUDE_GST(
        "rates_include_gst",
        "Rates include GST",
        PolicyCategory.GST_AND_QUOTING,
        PolicyValueKind.BOOLEAN,
        null,
        "Whether base truck & crew rates already include 10% GST",
        listOf(PolicyOption("false", "Exclusive (add 10% GST)"), PolicyOption("true", "Inclusive (1/11 GST inside)"))
    ),
    MINIMUM_HOURS(
        "minimum_hours",
        "Minimum hours",
        PolicyCategory.MINIMUMS_AND_INCREMENTS,
        PolicyValueKind.DECIMAL,
        "hours",
        "Minimum billable hours per service day (0–24)"
    ),
    BILLING_INCREMENT_MINUTES(
        "billing_increment_minutes",
        "Billing increment",
        PolicyCategory.MINIMUMS_AND_INCREMENTS,
        PolicyValueKind.INTEGER,
        "min",
        "Round-up interval in whole minutes (1–240)"
    ),
    CALL_OUT_FEE(
        "call_out_fee",
        "Call-out fee",
        PolicyCategory.CALL_OUT_AND_TRAVEL,
        PolicyValueKind.MONEY,
        "AUD",
        "Per-day call-out charge when requested ($0 allowed)"
    ),
    TRAVEL_FEE(
        "travel_fee",
        "Travel charge",
        PolicyCategory.CALL_OUT_AND_TRAVEL,
        PolicyValueKind.MONEY,
        "AUD",
        "Per-day travel charge when requested ($0 allowed)"
    ),
    WEEKEND_MULTIPLIER(
        "weekend_multiplier",
        "Weekend surcharge multiplier",
        PolicyCategory.SURCHARGES_AND_OVERTIME,
        PolicyValueKind.DECIMAL,
        "x",
        "Multiplier for Saturday / Sunday moves (>0 to 10)"
    ),
    PUBLIC_HOLIDAY_MULTIPLIER(
        "public_holiday_multiplier",
        "Public holiday multiplier",
        PolicyCategory.SURCHARGES_AND_OVERTIME,
        PolicyValueKind.DECIMAL,
        "x",
        "Multiplier for public holidays (>0 to 10)"
    ),
    OVERTIME_MULTIPLIER(
        "overtime_multiplier",
        "Overtime multiplier",
        PolicyCategory.SURCHARGES_AND_OVERTIME,
        PolicyValueKind.DECIMAL,
        "x",
        "Multiplier after standard working hours (>0 to 10)"
    ),
    STANDARD_DAY_HOURS(
        "standard_day_hours",
        "Standard working hours",
        PolicyCategory.MINIMUMS_AND_INCREMENTS,
        PolicyValueKind.DECIMAL,
        "hours",
        "Hours per day before overtime applies (>0 to 24)"
    ),
    DEPOSIT_METHOD(
        "deposit_method",
        "Deposit method",
        PolicyCategory.DEPOSITS_AND_VALIDITY,
        PolicyValueKind.ENUM,
        null,
        "How booking deposits are requested",
        listOf(
            PolicyOption("percent", "Percentage of total"),
            PolicyOption("fixed", "Fixed dollar amount"),
            PolicyOption("none", "No deposit required")
        )
    ),
    DEPOSIT_PERCENT(
        "deposit_percent",
        "Deposit percentage",
        PolicyCategory.DEPOSITS_AND_VALIDITY,
        PolicyValueKind.DECIMAL,
        "%",
        "Percentage of estimated total (0–100)"
    ),
    DEPOSIT_FIXED(
        "deposit_fixed",
        "Fixed deposit amount",
        PolicyCategory.DEPOSITS_AND_VALIDITY,
        PolicyValueKind.MONEY,
        "AUD",
        "Fixed deposit amount in dollars"
    ),
    QUOTE_VALIDITY_DAYS(
        "quote_validity_days",
        "Quotation validity",
        PolicyCategory.DEPOSITS_AND_VALIDITY,
        PolicyValueKind.INTEGER,
        "days",
        "Days until an issued quotation expires (1–365)"
    ),
    CANCELLATION_FEE(
        "cancellation_fee",
        "Cancellation fee",
        PolicyCategory.DEPOSITS_AND_VALIDITY,
        PolicyValueKind.MONEY,
        "AUD",
        "Configured cancellation policy fee ($0 allowed)"
    ),
    PACKING_RATE(
        "packing_rate",
        "Default packing rate",
        PolicyCategory.ADDITIONAL_SERVICES,
        PolicyValueKind.MONEY,
        "AUD",
        "Per-unit packing rate"
    ),
    HEAVY_ITEM_SURCHARGE(
        "heavy_item_surcharge",
        "Heavy item surcharge",
        PolicyCategory.ADDITIONAL_SERVICES,
        PolicyValueKind.MONEY,
        "AUD",
        "Per-item surcharge for heavy items"
    );

    companion object {
        fun fromKey(raw: String?): PricingPolicyKey? = entries.firstOrNull { it.key == raw }
    }
}

enum class ServiceUnit(val key: String, val label: String) {
    PER_ITEM("per_item", "Per item"),
    HOURLY("hourly", "Hourly"),
    PER_DAY("per_day", "Per day"),
    FIXED("fixed", "Fixed price");

    companion object {
        fun fromKey(raw: String?): ServiceUnit = entries.firstOrNull { it.key == raw } ?: PER_ITEM
    }
}

enum class GstTreatment(val key: String, val label: String) {
    INCLUSIVE("inclusive", "Incl. GST"),
    EXCLUSIVE("exclusive", "Excl. GST"),
    NONE("none", "No GST");

    companion object {
        fun fromKey(raw: String?): GstTreatment? = entries.firstOrNull { it.key == raw }
    }
}

data class PricingPolicyRecord(
    val id: String,
    val segment: String,
    val key: String,
    val value: String,
    val effectiveFrom: String,
    val status: String,
    val notes: String = "",
    val supersedesId: String? = null,
    val createdBy: String = "",
    val createdAt: String = "",
    val approvedBy: String? = null,
    val approvedAt: String? = null
)

data class ServiceCatalogueItem(
    val id: String,
    val name: String,
    val description: String,
    val segment: String?,
    val unit: ServiceUnit,
    val rate: String?,
    val gstTreatment: GstTreatment?,
    val effectiveFrom: String,
    val effectiveTo: String?,
    val active: Boolean,
    val supersedesId: String? = null
)

enum class ServicePricingStatus { UNCONFIGURED, ZERO, PRICED }

data class PartitionedPolicies(
    val activeByKey: Map<String, PricingPolicyRecord>,
    val pendingDrafts: List<PricingPolicyRecord>,
    val historicalAndFuture: List<PricingPolicyRecord>
)

data class VerifiedResidentialBenchmark(
    val label: String,
    val halfHourRate: String,
    val hourlyRate: String,
    val movers: Int
)

object PricingAdminLogic {
    private val MONEY_REGEX = Regex("^\\d{1,7}(\\.\\d{1,2})?$")
    private val DECIMAL_REGEX = Regex("^\\d{1,3}(\\.\\d{1,4})?$")
    private val INTEGER_REGEX = Regex("^\\d{1,4}$")

    val verifiedResidentialBenchmarks = listOf(
        VerifiedResidentialBenchmark("Small 8t (2 movers)", "69.00", "138.00", 2),
        VerifiedResidentialBenchmark("Medium 12t (2 movers)", "74.00", "148.00", 2),
        VerifiedResidentialBenchmark("Large 16t (2 movers)", "79.00", "158.00", 2),
        VerifiedResidentialBenchmark("Three movers + truck", "99.00", "198.00", 3)
    )

    fun partitionPolicies(
        policies: List<PricingPolicyRecord>,
        segment: String,
        onDate: String
    ): PartitionedPolicies {
        val scoped = policies.filter { it.segment == segment }
        val drafts = scoped.filter { it.status == "draft" }
        val approved = scoped
            .filter { it.status == "approved" }
            .sortedWith(
                compareByDescending<PricingPolicyRecord> { it.effectiveFrom }
                    .thenByDescending { it.approvedAt.orEmpty() }
                    .thenByDescending { it.id }
            )
        val active = linkedMapOf<String, PricingPolicyRecord>()
        val historical = mutableListOf<PricingPolicyRecord>()
        for (row in approved) {
            if (row.effectiveFrom <= onDate && row.key !in active) {
                active[row.key] = row
            } else {
                historical += row
            }
        }
        return PartitionedPolicies(activeByKey = active, pendingDrafts = drafts, historicalAndFuture = historical)
    }

    fun formatHalfHourRate(hourlyRate: String): String? {
        val trimmed = hourlyRate.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val d = BigDecimal(trimmed)
            if (d < BigDecimal.ZERO) null else d.divide(BigDecimal(2), 2, RoundingMode.HALF_UP).toPlainString()
        }.getOrNull()
    }

    /** Clearly distinguishes an unconfigured service (rate or GST null) from an explicitly approved $0.00 price. */
    fun servicePricingStatus(rate: String?, gstTreatment: GstTreatment?): ServicePricingStatus {
        if (rate.isNullOrBlank() || gstTreatment == null) return ServicePricingStatus.UNCONFIGURED
        val dec = runCatching { BigDecimal(rate.trim()) }.getOrNull() ?: return ServicePricingStatus.UNCONFIGURED
        return if (dec.compareTo(BigDecimal.ZERO) == 0) ServicePricingStatus.ZERO else ServicePricingStatus.PRICED
    }

    fun filterServices(
        services: List<ServiceCatalogueItem>,
        query: String,
        segmentFilter: String?, // null = all, "shared" = segment == null, else segment key
        unitFilter: ServiceUnit?,
        statusFilter: ServicePricingStatus?,
        includeHistorical: Boolean
    ): List<ServiceCatalogueItem> {
        val q = query.trim().lowercase()
        return services.filter { s ->
            if (!includeHistorical && s.effectiveTo != null) return@filter false
            when (segmentFilter) {
                "shared" -> if (s.segment != null) return@filter false
                null -> Unit
                else -> if (s.segment != segmentFilter) return@filter false
            }
            if (unitFilter != null && s.unit != unitFilter) return@filter false
            if (statusFilter != null && servicePricingStatus(s.rate, s.gstTreatment) != statusFilter) return@filter false
            if (q.isNotEmpty() && !s.name.lowercase().contains(q) && !s.description.lowercase().contains(q)) return@filter false
            true
        }
    }

    /** Returns null when valid, or a clear error message matching backend lib/pricing/policies.ts rules. */
    fun validatePolicyValue(policyKey: PricingPolicyKey, rawValue: String): String? {
        val v = rawValue.trim()
        if (v.isEmpty()) return "Enter a value for ${policyKey.label}."
        return when (policyKey) {
            PricingPolicyKey.RATES_INCLUDE_GST ->
                if (v == "true" || v == "false") null else "Must be true or false."
            PricingPolicyKey.DEPOSIT_METHOD ->
                if (v in setOf("percent", "fixed", "none")) null else "Must be percent, fixed or none."
            PricingPolicyKey.MINIMUM_HOURS -> {
                val n = v.toDoubleOrNull()
                if (DECIMAL_REGEX.matches(v) && n != null && n <= 24.0) null else "Must be hours between 0 and 24."
            }
            PricingPolicyKey.STANDARD_DAY_HOURS -> {
                val n = v.toDoubleOrNull()
                if (DECIMAL_REGEX.matches(v) && n != null && n > 0.0 && n <= 24.0) null else "Must be hours above 0, at most 24."
            }
            PricingPolicyKey.BILLING_INCREMENT_MINUTES -> {
                val n = v.toIntOrNull()
                if (INTEGER_REGEX.matches(v) && n != null && n in 1..240) null else "Must be whole minutes between 1 and 240."
            }
            PricingPolicyKey.QUOTE_VALIDITY_DAYS -> {
                val n = v.toIntOrNull()
                if (INTEGER_REGEX.matches(v) && n != null && n in 1..365) null else "Must be whole days between 1 and 365."
            }
            PricingPolicyKey.DEPOSIT_PERCENT -> {
                val n = v.toDoubleOrNull()
                if (DECIMAL_REGEX.matches(v) && n != null && n <= 100.0) null else "Must be a percentage between 0 and 100."
            }
            PricingPolicyKey.WEEKEND_MULTIPLIER,
            PricingPolicyKey.PUBLIC_HOLIDAY_MULTIPLIER,
            PricingPolicyKey.OVERTIME_MULTIPLIER -> {
                val n = v.toDoubleOrNull()
                if (DECIMAL_REGEX.matches(v) && n != null && n > 0.0 && n <= 10.0) null else "Must be a multiplier above 0, at most 10."
            }
            PricingPolicyKey.CALL_OUT_FEE,
            PricingPolicyKey.TRAVEL_FEE,
            PricingPolicyKey.DEPOSIT_FIXED,
            PricingPolicyKey.CANCELLATION_FEE,
            PricingPolicyKey.PACKING_RATE,
            PricingPolicyKey.HEAVY_ITEM_SURCHARGE ->
                if (MONEY_REGEX.matches(v)) null else "Must be a valid dollar amount."
        }
    }
}

object PricingRepository {
    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k, "").trim().takeIf { it.isNotEmpty() }

    internal fun parsePolicies(jsonText: String): List<PricingPolicyRecord> {
        val arr = JSONObject(jsonText).optJSONArray("policies") ?: JSONArray()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PricingPolicyRecord(
                id = o.getString("id"),
                segment = o.getString("segment"),
                key = o.getString("key"),
                value = o.getString("value"),
                effectiveFrom = o.getString("effective_from"),
                status = o.getString("status"),
                notes = o.optString("notes", ""),
                supersedesId = o.str("supersedes_id"),
                createdBy = o.optString("created_by", ""),
                createdAt = o.optString("created_at", ""),
                approvedBy = o.str("approved_by"),
                approvedAt = o.str("approved_at")
            )
        }
    }

    internal fun parseServices(jsonText: String): List<ServiceCatalogueItem> {
        val arr = JSONObject(jsonText).optJSONArray("services") ?: JSONArray()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ServiceCatalogueItem(
                id = o.getString("id"),
                name = o.getString("name"),
                description = o.optString("description", ""),
                segment = o.str("segment"),
                unit = ServiceUnit.fromKey(o.optString("unit")),
                rate = o.str("rate"),
                gstTreatment = GstTreatment.fromKey(o.str("gst_treatment")),
                effectiveFrom = o.getString("effective_from"),
                effectiveTo = o.str("effective_to"),
                active = o.optBoolean("active", true),
                supersedesId = o.str("supersedes_id")
            )
        }
    }

    internal fun buildSavePolicyDraftPayload(
        segment: String,
        key: String,
        value: String,
        effectiveFrom: String,
        notes: String
    ): JSONObject = JSONObject()
        .put("action", "save")
        .put("segment", segment)
        .put("key", key)
        .put("value", value.trim())
        .put("effectiveFrom", effectiveFrom.trim())
        .put("notes", notes.trim())

    internal fun buildApprovePolicyPayload(id: String): JSONObject = JSONObject()
        .put("action", "approve")
        .put("id", id)

    internal fun buildSaveRateCardPayload(
        id: String?,
        name: String,
        segment: String,
        truckClass: String,
        movers: Int,
        hourlyRate: String,
        callOutHours: String,
        callOutFee: String,
        overtimeRate: String?,
        effectiveFrom: String,
        active: Boolean,
        notes: String
    ): JSONObject = JSONObject()
        .put("id", id ?: JSONObject.NULL)
        .put("name", name.trim())
        .put("segment", segment)
        .put("truckClass", truckClass.trim())
        .put("movers", movers)
        .put("hourlyRate", hourlyRate.trim())
        .put("callOutHours", callOutHours.trim().ifBlank { "0" })
        .put("callOutFee", callOutFee.trim().ifBlank { "0" })
        .put("overtimeRate", overtimeRate?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        .put("effectiveFrom", effectiveFrom.trim())
        .put("active", active)
        .put("notes", notes.trim())

    internal fun buildSaveServicePayload(
        id: String?,
        name: String,
        description: String,
        segment: String?,
        unit: ServiceUnit,
        rate: String?,
        gstTreatment: GstTreatment?,
        effectiveFrom: String,
        active: Boolean
    ): JSONObject = JSONObject()
        .put("id", id ?: JSONObject.NULL)
        .put("name", name.trim())
        .put("description", description.trim())
        .put("segment", segment?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        .put("unit", unit.key)
        .put("rate", rate?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        .put("gstTreatment", gstTreatment?.key ?: JSONObject.NULL)
        .put("effectiveFrom", effectiveFrom.trim())
        .put("active", active)

    suspend fun fetchPolicies(): ApiResult<List<PricingPolicyRecord>> =
        ApiClient.get("/api/pricing/policies", ::parsePolicies)

    suspend fun savePolicyDraft(
        segment: String,
        key: String,
        value: String,
        effectiveFrom: String,
        notes: String
    ): ApiResult<String> = ApiClient.post(
        "/api/pricing/policies",
        buildSavePolicyDraftPayload(segment, key, value, effectiveFrom, notes)
    ) { JSONObject(it).getString("id") }

    suspend fun approvePolicy(id: String): ApiResult<String> = ApiClient.post(
        "/api/pricing/policies",
        buildApprovePolicyPayload(id)
    ) { JSONObject(it).getString("id") }

    suspend fun saveRateCard(
        id: String?,
        name: String,
        segment: String,
        truckClass: String,
        movers: Int,
        hourlyRate: String,
        callOutHours: String,
        callOutFee: String,
        overtimeRate: String?,
        effectiveFrom: String,
        active: Boolean,
        notes: String
    ): ApiResult<Pair<String, Boolean>> = ApiClient.post(
        "/api/rate-cards",
        buildSaveRateCardPayload(
            id, name, segment, truckClass, movers, hourlyRate,
            callOutHours, callOutFee, overtimeRate, effectiveFrom, active, notes
        )
    ) { text ->
        val o = JSONObject(text)
        o.getString("id") to o.optBoolean("versionCreated", false)
    }

    suspend fun fetchServices(): ApiResult<List<ServiceCatalogueItem>> =
        ApiClient.get("/api/pricing/services", ::parseServices)

    suspend fun saveService(
        id: String?,
        name: String,
        description: String,
        segment: String?,
        unit: ServiceUnit,
        rate: String?,
        gstTreatment: GstTreatment?,
        effectiveFrom: String,
        active: Boolean
    ): ApiResult<Pair<String, Boolean>> = ApiClient.post(
        "/api/pricing/services",
        buildSaveServicePayload(id, name, description, segment, unit, rate, gstTreatment, effectiveFrom, active)
    ) { text ->
        val o = JSONObject(text)
        o.getString("id") to o.optBoolean("versionCreated", false)
    }
}
