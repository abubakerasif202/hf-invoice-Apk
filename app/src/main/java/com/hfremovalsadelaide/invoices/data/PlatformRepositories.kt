package com.hfremovalsadelaide.invoices.data

import com.hfremovalsadelaide.invoices.network.ApiClient
import com.hfremovalsadelaide.invoices.network.ApiResult
import org.json.JSONArray
import org.json.JSONObject

data class StaffMember(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val employmentType: String,
    val abn: String = "",
    val hourlyRate: String? = null,
    val overtimeMultiplier: String = "1.50",
    val active: Boolean = true,
    val hasPassword: Boolean = false,
    val hasPasskey: Boolean = false
)

data class VehicleRecord(
    val id: String,
    val name: String,
    val registration: String,
    val vehicleClass: String,
    val capacityTonnes: String?,
    val capacityM3: String?,
    val defaultDriverId: String?,
    val defaultDriverName: String?,
    val status: String,
    val regoExpiry: String?,
    val nextServiceDate: String?,
    val notes: String
)

data class VehicleLogRecord(
    val id: String,
    val vehicleId: String,
    val kind: String,
    val occurredOn: String,
    val cost: String?,
    val odometer: Int?,
    val vendor: String,
    val notes: String
)

data class FleetAlert(
    val vehicleId: String,
    val vehicleName: String,
    val kind: String,
    val dueDate: String?,
    val message: String
)

data class FleetOverlap(
    val vehicleId: String,
    val vehicleName: String,
    val jobIds: Pair<String, String>,
    val titles: Pair<String, String>
)

data class FleetSnapshot(
    val vehicles: List<VehicleRecord>,
    val logs: List<VehicleLogRecord>,
    val alerts: List<FleetAlert>,
    val overlaps: List<FleetOverlap>
)

data class RateCard(
    val id: String,
    val name: String,
    val segment: String,
    val truckClass: String,
    val movers: Int,
    val hourlyRate: String,
    val callOutHours: String,
    val callOutFee: String,
    val effectiveFrom: String,
    val active: Boolean,
    val overtimeRate: String? = null,
    val effectiveTo: String? = null,
    val notes: String = "",
    val supersedesId: String? = null
)

data class QuoteRecord(
    val id: String,
    val reference: String,
    val customerId: String?,
    val customerName: String,
    val stage: String,
    val segment: String,
    val preferredDate: String?,
    val pickup: String,
    val delivery: String,
    val truckClass: String,
    val movers: Int,
    val estimatedHours: String?,
    val quotedRate: String?,
    val estimatedTotal: String?,
    val notes: String,
    val followUpDate: String?,
    val duplicateOfCustomerId: String?
)

data class FollowUpRecord(
    val id: String,
    val customerId: String,
    val customerName: String,
    val dueDate: String,
    val title: String,
    val notes: String,
    val completedAt: String?
)

data class PortalSubmissionRecord(
    val id: String,
    val customerId: String,
    val customerName: String,
    val jobId: String?,
    val kind: String,
    val payloadSummary: String,
    val status: String,
    val createdAt: String
)

data class CrmSnapshot(
    val customers: List<CustomerSummary>,
    val quotes: List<QuoteRecord>,
    val followUps: List<FollowUpRecord>,
    val portalSubmissions: List<PortalSubmissionRecord>
)

data class TimesheetRecord(
    val id: String,
    val staffId: String,
    val staffName: String,
    val role: String,
    val employmentType: String,
    val jobTitle: String?,
    val workDate: String,
    val breakMinutes: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val hourlyRate: String?,
    val reimbursements: String,
    val reimbursementNotes: String,
    val status: String,
    val notes: String
)

data class PaymentStatementRecord(
    val id: String,
    val statementNumber: String,
    val staffId: String,
    val staffName: String,
    val employmentType: String,
    val periodStart: String,
    val periodEnd: String,
    val grossPay: String,
    val reimbursements: String,
    val totalPayable: String,
    val status: String,
    val paymentReference: String
)

data class TimesheetsSnapshot(
    val timesheets: List<TimesheetRecord>,
    val statements: List<PaymentStatementRecord>
)

data class MonthlyTrendPoint(
    val month: String,
    val invoicedInclGst: String,
    val invoicedExclGst: String,
    val paymentsReceived: String,
    val jobsCount: Int
)

data class TruckUtilItem(
    val truck: String,
    val jobs: Int,
    val workingMinutes: Int
)

data class OpsConflict(val jobA: String, val jobB: String, val reasons: List<String>)

data class OpsMaintenanceItem(
    val vehicleId: String,
    val vehicleName: String,
    val kind: String,
    val dueDate: String?,
    val message: String
)

/** Live operational counts computed by the backend (lib/ops-snapshot.ts). Null when the backend predates it. */
data class OperationsSnapshot(
    val confirmedToday: Int,
    val tentativeToday: Int,
    val confirmedTomorrow: Int,
    val tentativeTomorrow: Int,
    val activeJobs: Int,
    val unassignedBookings: Int,
    val unassignedCrew: Int,
    val unassignedTruck: Int,
    val trucksTotal: Int,
    val trucksUnavailable: Int,
    val trucksOnJobsToday: Int,
    val trucksAvailableToday: Int,
    /** Distinct people on today's jobs; each person counted once. */
    val staffScheduledToday: Int,
    /** Subset of those on a job that is in progress or paused right now. */
    val staffWorkingNow: Int,
    val staffOnLeaveToday: Int,
    val crewConflicts: List<OpsConflict>,
    val maintenanceDue: List<OpsMaintenanceItem>
)

internal fun parseOperationsSnapshot(root: JSONObject): OperationsSnapshot? {
    val o = root.optJSONObject("operations") ?: return null
    val conflicts = o.optJSONArray("crewConflicts") ?: JSONArray()
    val maintenance = o.optJSONArray("maintenanceDue") ?: JSONArray()
    return OperationsSnapshot(
        confirmedToday = o.optInt("confirmedToday", 0),
        tentativeToday = o.optInt("tentativeToday", 0),
        confirmedTomorrow = o.optInt("confirmedTomorrow", 0),
        tentativeTomorrow = o.optInt("tentativeTomorrow", 0),
        activeJobs = o.optInt("activeJobs", 0),
        unassignedBookings = o.optInt("unassignedBookings", 0),
        unassignedCrew = o.optInt("unassignedCrew", 0),
        unassignedTruck = o.optInt("unassignedTruck", 0),
        trucksTotal = o.optInt("trucksTotal", 0),
        trucksUnavailable = o.optInt("trucksUnavailable", 0),
        trucksOnJobsToday = o.optInt("trucksOnJobsToday", 0),
        trucksAvailableToday = o.optInt("trucksAvailableToday", 0),
        staffScheduledToday = o.optInt("staffScheduledToday", 0),
        staffWorkingNow = o.optInt("staffWorkingNow", 0),
        staffOnLeaveToday = o.optInt("staffOnLeaveToday", 0),
        crewConflicts = List(conflicts.length()) { i ->
            val c = conflicts.getJSONObject(i)
            val reasons = c.optJSONArray("reasons") ?: JSONArray()
            OpsConflict(c.getString("jobA"), c.getString("jobB"), List(reasons.length()) { r -> reasons.getString(r) })
        },
        maintenanceDue = List(maintenance.length()) { i ->
            val m = maintenance.getJSONObject(i)
            OpsMaintenanceItem(
                vehicleId = m.getString("vehicleId"),
                vehicleName = m.getString("vehicleName"),
                kind = m.getString("kind"),
                dueDate = if (m.isNull("dueDate")) null else m.optString("dueDate").ifBlank { null },
                message = m.getString("message")
            )
        }
    )
}

data class AnalyticsSnapshot(
    val jobsToday: Int,
    val jobsThisWeek: Int,
    val upcomingBookings: Int,
    val completedJobs: Int,
    val averageJobDurationMinutes: Int,
    val crewHours: Double,
    val revenueInvoicedExclGst: String,
    val gstInvoiced: String,
    val revenueInvoicedInclGst: String,
    val paymentsReceived: String,
    val outstandingBalance: String,
    val overdueInvoices: Int,
    val overdueBalance: String,
    val additionalCharges: String,
    val customerRepeatRate: Double,
    val truckUtilisation: List<TruckUtilItem>,
    val monthlyTrends: List<MonthlyTrendPoint>,
    val operations: OperationsSnapshot? = null
)

data class NotificationFeedItem(
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    val jobId: String?,
    val invoiceId: String?,
    val readAt: String?,
    val createdAt: String
)

object PlatformRepository {
    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k, "").trim().takeIf { it.isNotEmpty() }

    suspend fun fetchStaff(): ApiResult<List<StaffMember>> = ApiClient.get("/api/staff") { text ->
        val arr = JSONObject(text).optJSONArray("staff") ?: JSONArray()
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            StaffMember(
                id = o.getString("id"),
                name = o.getString("name"),
                email = o.getString("email"),
                phone = o.optString("phone", ""),
                role = o.getString("role"),
                employmentType = o.optString("employment_type", "employee"),
                abn = o.optString("abn", ""),
                hourlyRate = o.str("hourly_rate"),
                overtimeMultiplier = o.optString("overtime_multiplier", "1.50"),
                active = o.optBoolean("active", true),
                hasPassword = o.optBoolean("has_password", false),
                hasPasskey = o.optBoolean("has_passkey", false)
            )
        }
    }

    suspend fun createStaff(
        name: String,
        email: String,
        phone: String,
        role: String,
        employmentType: String,
        hourlyRate: String?
    ): ApiResult<String> = ApiClient.post(
        "/api/staff",
        JSONObject()
            .put("action", "create")
            .put("name", name)
            .put("email", email)
            .put("phone", phone)
            .put("role", role)
            .put("employmentType", employmentType)
            .put("hourlyRate", hourlyRate?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
    ) { text -> JSONObject(text).optString("inviteToken", "") }

    suspend fun resetStaffPassword(staffId: String): ApiResult<String> =
        ApiClient.post("/api/staff", JSONObject().put("action", "reset_password").put("staffId", staffId)) {
            JSONObject(it).optString("resetToken", "")
        }

    suspend fun setStaffActive(staffId: String, active: Boolean): ApiResult<Boolean> =
        ApiClient.post("/api/staff", JSONObject().put("action", "revoke").put("staffId", staffId).put("active", active)) { true }

    suspend fun fetchFleet(): ApiResult<FleetSnapshot> = ApiClient.get("/api/fleet") { text ->
        val root = JSONObject(text)
        val vArr = root.optJSONArray("vehicles") ?: JSONArray()
        val vehicles = List(vArr.length()) { i ->
            val o = vArr.getJSONObject(i)
            VehicleRecord(
                id = o.getString("id"),
                name = o.getString("name"),
                registration = o.optString("registration", ""),
                vehicleClass = o.optString("vehicle_class", ""),
                capacityTonnes = o.str("capacity_tonnes"),
                capacityM3 = o.str("capacity_m3"),
                defaultDriverId = o.str("default_driver_id"),
                defaultDriverName = o.str("default_driver_name"),
                status = o.optString("status", "available"),
                regoExpiry = o.str("rego_expiry"),
                nextServiceDate = o.str("next_service_date"),
                notes = o.optString("notes", "")
            )
        }
        val lArr = root.optJSONArray("logs") ?: JSONArray()
        val logs = List(lArr.length()) { i ->
            val o = lArr.getJSONObject(i)
            VehicleLogRecord(
                id = o.getString("id"),
                vehicleId = o.getString("vehicle_id"),
                kind = o.getString("kind"),
                occurredOn = o.getString("occurred_on"),
                cost = o.str("cost"),
                odometer = if (o.isNull("odometer")) null else o.optInt("odometer"),
                vendor = o.optString("vendor", ""),
                notes = o.optString("notes", "")
            )
        }
        val aArr = root.optJSONArray("alerts") ?: JSONArray()
        val alerts = List(aArr.length()) { i ->
            val o = aArr.getJSONObject(i)
            FleetAlert(o.getString("vehicleId"), o.getString("vehicleName"), o.getString("kind"), o.str("dueDate"), o.getString("message"))
        }
        val oArr = root.optJSONArray("overlaps") ?: JSONArray()
        val overlaps = List(oArr.length()) { i ->
            val o = oArr.getJSONObject(i)
            val jIds = o.getJSONArray("jobIds")
            val titles = o.getJSONArray("titles")
            FleetOverlap(o.getString("vehicleId"), o.getString("vehicleName"), jIds.getString(0) to jIds.getString(1), titles.getString(0) to titles.getString(1))
        }
        FleetSnapshot(vehicles, logs, alerts, overlaps)
    }

    suspend fun saveVehicle(
        id: String?,
        name: String,
        registration: String,
        vehicleClass: String,
        capacityTonnes: String?,
        status: String,
        regoExpiry: String?,
        nextServiceDate: String?,
        notes: String
    ): ApiResult<String> = ApiClient.post(
        "/api/fleet",
        JSONObject()
            .put("action", "save_vehicle")
            .put("id", id ?: JSONObject.NULL)
            .put("name", name)
            .put("registration", registration)
            .put("vehicleClass", vehicleClass)
            .put("capacityTonnes", capacityTonnes?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("status", status)
            .put("regoExpiry", regoExpiry?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("nextServiceDate", nextServiceDate?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("notes", notes)
    ) { JSONObject(it).getString("id") }

    suspend fun addVehicleLog(
        vehicleId: String,
        kind: String,
        occurredOn: String,
        cost: String?,
        odometer: Int?,
        vendor: String,
        notes: String
    ): ApiResult<String> = ApiClient.post(
        "/api/fleet",
        JSONObject()
            .put("action", "add_log")
            .put("vehicleId", vehicleId)
            .put("kind", kind)
            .put("occurredOn", occurredOn)
            .put("cost", cost?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("odometer", odometer ?: JSONObject.NULL)
            .put("vendor", vendor)
            .put("notes", notes)
    ) { JSONObject(it).getString("id") }

    suspend fun fetchRateCards(): ApiResult<List<RateCard>> = ApiClient.get("/api/rate-cards") { text ->
        val arr = JSONObject(text).optJSONArray("rateCards") ?: JSONArray()
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RateCard(
                id = o.getString("id"),
                name = o.getString("name"),
                segment = o.getString("segment"),
                truckClass = o.optString("truck_class", ""),
                movers = o.optInt("movers", 2),
                hourlyRate = o.getString("hourly_rate"),
                callOutHours = o.optString("call_out_hours", "0"),
                callOutFee = o.optString("call_out_fee", "0"),
                effectiveFrom = o.optString("effective_from", ""),
                active = o.optBoolean("active", true),
                overtimeRate = o.str("overtime_rate"),
                effectiveTo = o.str("effective_to"),
                notes = o.optString("notes", ""),
                supersedesId = o.str("supersedes_id")
            )
        }
    }

    suspend fun fetchCrm(): ApiResult<CrmSnapshot> = ApiClient.get("/api/crm") { text ->
        val root = JSONObject(text)
        val cArr = root.optJSONArray("customers") ?: JSONArray()
        val customers = List(cArr.length()) { i ->
            val o = cArr.getJSONObject(i)
            val d = o.getJSONObject("data")
            CustomerSummary(
                id = o.getString("id"),
                name = d.optString("name", ""),
                address = d.optString("address", ""),
                email = d.optString("email", ""),
                phone = d.optString("phone", ""),
                kind = d.optString("kind", "Individual"),
                notes = d.optString("notes", "")
            )
        }.sortedBy { it.name.lowercase() }
        val qArr = root.optJSONArray("quotes") ?: JSONArray()
        val quotes = List(qArr.length()) { i ->
            val o = qArr.getJSONObject(i)
            QuoteRecord(
                id = o.getString("id"),
                reference = o.getString("reference"),
                customerId = o.str("customer_id"),
                customerName = o.optString("customer_name", "Lead / New enquiry"),
                stage = o.getString("stage"),
                segment = o.optString("segment", "residential"),
                preferredDate = o.str("preferred_date"),
                pickup = o.optString("pickup", ""),
                delivery = o.optString("delivery", ""),
                truckClass = o.optString("truck_class", ""),
                movers = o.optInt("movers", 2),
                estimatedHours = o.str("estimated_hours"),
                quotedRate = o.str("quoted_rate"),
                estimatedTotal = o.str("estimated_total"),
                notes = o.optString("notes", ""),
                followUpDate = o.str("follow_up_date"),
                duplicateOfCustomerId = o.str("duplicate_of_customer_id")
            )
        }
        val fArr = root.optJSONArray("followUps") ?: JSONArray()
        val followUps = List(fArr.length()) { i ->
            val o = fArr.getJSONObject(i)
            FollowUpRecord(
                id = o.getString("id"),
                customerId = o.getString("customer_id"),
                customerName = o.optString("customer_name", ""),
                dueDate = o.getString("due_date"),
                title = o.getString("title"),
                notes = o.optString("notes", ""),
                completedAt = o.str("completed_at")
            )
        }
        val pArr = root.optJSONArray("portalSubmissions") ?: JSONArray()
        val submissions = List(pArr.length()) { i ->
            val o = pArr.getJSONObject(i)
            PortalSubmissionRecord(
                id = o.getString("id"),
                customerId = o.getString("customer_id"),
                customerName = o.optString("customer_name", ""),
                jobId = o.str("job_id"),
                kind = o.getString("kind"),
                payloadSummary = o.optJSONObject("payload")?.toString() ?: "",
                status = o.getString("status"),
                createdAt = o.getString("created_at")
            )
        }
        CrmSnapshot(customers, quotes, followUps, submissions)
    }

    suspend fun saveCustomer(id: String?, name: String, kind: String, email: String, phone: String, address: String, notes: String): ApiResult<Pair<String, Int>> =
        ApiClient.post(
            "/api/crm",
            JSONObject()
                .put("action", "save_customer")
                .put("id", id ?: JSONObject.NULL)
                .put("data", JSONObject()
                    .put("kind", kind)
                    .put("name", name)
                    .put("contact", "")
                    .put("email", email)
                    .put("phone", phone)
                    .put("address", address)
                    .put("abn", "")
                    .put("notes", notes))
        ) { text ->
            val o = JSONObject(text)
            o.getString("id") to (o.optJSONArray("potentialDuplicates")?.length() ?: 0)
        }

    suspend fun saveQuote(
        customerId: String?,
        newCustomerName: String?,
        stage: String,
        segment: String,
        pickup: String,
        delivery: String,
        truckClass: String,
        estimatedTotal: String?,
        notes: String
    ): ApiResult<String> {
        val body = JSONObject()
            .put("action", "save_quote")
            .put("customerId", customerId ?: JSONObject.NULL)
            .put("stage", stage)
            .put("segment", segment)
            .put("pickup", pickup)
            .put("delivery", delivery)
            .put("truckClass", truckClass)
            .put("estimatedTotal", estimatedTotal?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("notes", notes)
        if (customerId == null && !newCustomerName.isNullOrBlank()) {
            body.put("newCustomer", JSONObject().put("kind", "Individual").put("name", newCustomerName))
        }
        return ApiClient.post("/api/crm", body) { JSONObject(it).getString("id") }
    }

    suspend fun advanceQuote(quoteId: String, stage: String): ApiResult<Boolean> =
        ApiClient.post("/api/crm", JSONObject().put("action", "advance_quote").put("quoteId", quoteId).put("stage", stage)) { true }

    suspend fun createPortalLink(customerId: String, jobId: String? = null, quoteId: String? = null): ApiResult<String> =
        ApiClient.post(
            "/api/crm",
            JSONObject()
                .put("action", "create_portal_link")
                .put("customerId", customerId)
                .put("jobId", jobId ?: JSONObject.NULL)
                .put("quoteId", quoteId ?: JSONObject.NULL)
        ) { JSONObject(it).getString("url") }

    suspend fun reviewPortalSubmission(submissionId: String, decision: String, notes: String = ""): ApiResult<Boolean> =
        ApiClient.post(
            "/api/crm",
            JSONObject()
                .put("action", "review_portal_submission")
                .put("submissionId", submissionId)
                .put("decision", decision)
                .put("reviewNotes", notes)
        ) { true }

    suspend fun renderTemplate(kind: String, customerName: String, reference: String?, amount: String?): ApiResult<Pair<String, String>> =
        ApiClient.post(
            "/api/crm",
            JSONObject()
                .put("action", "render_template")
                .put("kind", kind)
                .put("customerName", customerName)
                .put("reference", reference ?: "")
                .put("amount", amount ?: "")
        ) { text ->
            val o = JSONObject(text)
            o.getString("subject") to o.getString("body")
        }

    suspend fun fetchTimesheets(): ApiResult<TimesheetsSnapshot> = ApiClient.get("/api/timesheets") { text ->
        val root = JSONObject(text)
        val tArr = root.optJSONArray("timesheets") ?: JSONArray()
        val timesheets = List(tArr.length()) { i ->
            val o = tArr.getJSONObject(i)
            TimesheetRecord(
                id = o.getString("id"),
                staffId = o.getString("staff_id"),
                staffName = o.getString("staff_name"),
                role = o.getString("role"),
                employmentType = o.optString("employment_type", "employee"),
                jobTitle = o.str("job_title"),
                workDate = o.getString("work_date"),
                breakMinutes = o.optInt("break_minutes", 0),
                regularMinutes = o.optInt("regular_minutes", 0),
                overtimeMinutes = o.optInt("overtime_minutes", 0),
                hourlyRate = o.str("hourly_rate"),
                reimbursements = o.optString("reimbursements", "0.00"),
                reimbursementNotes = o.optString("reimbursement_notes", ""),
                status = o.getString("status"),
                notes = o.optString("notes", "")
            )
        }
        val sArr = root.optJSONArray("statements") ?: JSONArray()
        val statements = List(sArr.length()) { i ->
            val o = sArr.getJSONObject(i)
            PaymentStatementRecord(
                id = o.getString("id"),
                statementNumber = o.getString("statement_number"),
                staffId = o.getString("staff_id"),
                staffName = o.getString("staff_name"),
                employmentType = o.optString("employment_type", "employee"),
                periodStart = o.getString("period_start"),
                periodEnd = o.getString("period_end"),
                grossPay = o.getString("gross_pay"),
                reimbursements = o.getString("reimbursements"),
                totalPayable = o.getString("total_payable"),
                status = o.getString("status"),
                paymentReference = o.optString("payment_reference", "")
            )
        }
        TimesheetsSnapshot(timesheets, statements)
    }

    suspend fun submitOwnTimesheet(
        workDate: String,
        regularMinutes: Int,
        overtimeMinutes: Int,
        breakMinutes: Int,
        reimbursements: String,
        notes: String
    ): ApiResult<String> = ApiClient.post(
        "/api/timesheets",
        JSONObject()
            .put("action", "submit_own")
            .put("workDate", workDate)
            .put("regularMinutes", regularMinutes)
            .put("overtimeMinutes", overtimeMinutes)
            .put("breakMinutes", breakMinutes)
            .put("reimbursements", reimbursements.ifBlank { "0" })
            .put("notes", notes)
    ) { JSONObject(it).getString("id") }

    suspend fun approveTimesheet(timesheetId: String): ApiResult<Boolean> =
        ApiClient.post("/api/timesheets", JSONObject().put("action", "approve").put("timesheetId", timesheetId)) { true }

    suspend fun generatePaymentStatement(staffId: String, periodStart: String, periodEnd: String): ApiResult<String> =
        ApiClient.post(
            "/api/timesheets",
            JSONObject()
                .put("action", "generate_statement")
                .put("staffId", staffId)
                .put("periodStart", periodStart)
                .put("periodEnd", periodEnd)
        ) { JSONObject(it).getString("statement_number") }

    suspend fun fetchAnalytics(from: String? = null, to: String? = null): ApiResult<AnalyticsSnapshot> {
        val qs = buildList {
            if (!from.isNullOrBlank()) add("from=$from")
            if (!to.isNullOrBlank()) add("to=$to")
        }.joinToString("&").let { if (it.isNotEmpty()) "?$it" else "" }
        return ApiClient.get("/api/analytics$qs") { text ->
            val root = JSONObject(text)
            val k = root.getJSONObject("kpis")
            val tArr = root.optJSONArray("truckUtilisation") ?: JSONArray()
            val trucks = List(tArr.length()) { i ->
                val o = tArr.getJSONObject(i)
                TruckUtilItem(o.getString("truck"), o.optInt("jobs", 0), o.optInt("workingMinutes", 0))
            }
            val mArr = root.optJSONArray("monthlyTrends") ?: JSONArray()
            val trends = List(mArr.length()) { i ->
                val o = mArr.getJSONObject(i)
                MonthlyTrendPoint(
                    month = o.getString("month"),
                    invoicedInclGst = o.getString("invoicedInclGst"),
                    invoicedExclGst = o.getString("invoicedExclGst"),
                    paymentsReceived = o.getString("paymentsReceived"),
                    jobsCount = o.optInt("jobsCount", 0)
                )
            }
            AnalyticsSnapshot(
                jobsToday = k.optInt("jobsToday", 0),
                jobsThisWeek = k.optInt("jobsThisWeek", 0),
                upcomingBookings = k.optInt("upcomingBookings", 0),
                completedJobs = k.optInt("completedJobs", 0),
                averageJobDurationMinutes = k.optInt("averageJobDurationMinutes", 0),
                crewHours = k.optDouble("crewHours", 0.0),
                revenueInvoicedExclGst = k.optString("revenueInvoicedExclGst", "0.00"),
                gstInvoiced = k.optString("gstInvoiced", "0.00"),
                revenueInvoicedInclGst = k.optString("revenueInvoicedInclGst", "0.00"),
                paymentsReceived = k.optString("paymentsReceived", "0.00"),
                outstandingBalance = k.optString("outstandingBalance", "0.00"),
                overdueInvoices = k.optInt("overdueInvoices", 0),
                overdueBalance = k.optString("overdueBalance", "0.00"),
                additionalCharges = k.optString("additionalCharges", "0.00"),
                customerRepeatRate = k.optDouble("customerRepeatRate", 0.0),
                truckUtilisation = trucks,
                monthlyTrends = trends,
                operations = parseOperationsSnapshot(root)
            )
        }
    }

    suspend fun fetchNotifications(): ApiResult<List<NotificationFeedItem>> = ApiClient.get("/api/jobs/notifications") { text ->
        val arr = JSONObject(text).optJSONArray("events") ?: JSONArray()
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            NotificationFeedItem(
                id = o.getString("id"),
                category = o.getString("category"),
                title = o.getString("title"),
                body = o.getString("body"),
                jobId = o.str("job_id"),
                invoiceId = o.str("invoice_id"),
                readAt = o.str("read_at"),
                createdAt = o.getString("created_at")
            )
        }
    }

    suspend fun generateNotifications(): ApiResult<Int> =
        ApiClient.post("/api/jobs/notifications", JSONObject().put("action", "generate")) {
            JSONObject(it).optInt("createdCount", 0)
        }

    suspend fun markNotificationRead(eventId: String): ApiResult<Boolean> =
        ApiClient.post("/api/jobs/notifications", JSONObject().put("action", "mark_read").put("eventId", eventId)) { true }
}
