package com.hfremovalsadelaide.invoices.jobs

import java.time.Instant

enum class JobState(val server: String) {
    SCHEDULED("scheduled"), IN_PROGRESS("in_progress"), PAUSED("paused"), COMPLETED("completed");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.server == value } ?: SCHEDULED
    }
}

enum class BookingStatus {
    CONFIRMED, UNCONFIRMED, UNKNOWN;

    companion object {
        fun from(value: String?) = when (value) {
            "confirmed" -> CONFIRMED
            "unconfirmed" -> UNCONFIRMED
            else -> UNKNOWN
        }
    }
}

enum class SignoffStatus {
    NONE, SIGNED, SKIPPED;

    companion object {
        fun from(value: String?) = when (value) {
            "signed" -> SIGNED
            "skipped" -> SKIPPED
            else -> NONE
        }
    }
}

/** Mirrors the backend notification categories (server keys are used for preference sync). */
enum class NotificationCategory(val serverKey: String, val label: String) {
    UPCOMING_24H("upcoming_24h", "Upcoming job: 24 hours before"),
    UPCOMING_1H("upcoming_1h", "Upcoming job: 1 hour before"),
    STARTING_SOON_15M("starting_soon_15m", "Job starting soon: 15 minutes before"),
    SCHEDULED_START("scheduled_start", "Scheduled start reached"),
    RUNNING_LATE("running_late", "Job running past scheduled finish"),
    COMPLETED("completed", "Job completed"),
    SIGNOFF_PENDING("signoff_pending", "Customer sign-off pending"),
    RESCHEDULED_OR_CANCELLED("rescheduled_or_cancelled", "Booking rescheduled or cancelled"),
    ASSIGNMENT_CHANGED("assignment_changed", "Assigned-job changes"),
    JOB_CONFIRMED("job_confirmed", "Job confirmed"),
    TRUCK_UNAVAILABLE("truck_unavailable", "Truck unavailable or maintenance due"),
    INVOICE_OVERDUE("invoice_overdue", "Invoice overdue"),
    TIMESHEET_PENDING("timesheet_pending", "Staff timesheets awaiting review")
}

data class CrewEventItem(
    val id: String,
    val memberName: String,
    val eventType: String,
    val at: Instant?,
    val customerAcknowledged: Boolean,
    val acknowledgmentName: String,
    val notes: String
)

data class TimelineEntry(
    val kind: String,
    val at: String,
    val label: String,
    val actor: String,
    val detail: String
)

data class ConditionReportItem(
    val id: String,
    val phase: String,
    val accessConditions: String,
    val existingDamage: String,
    val delaysOrIssues: String,
    val additionalServices: String,
    val checklist: Map<String, Boolean>,
    val customerAcknowledged: Boolean,
    val acknowledgmentName: String,
    val notes: String
)

data class JobPhotoMeta(
    val id: String,
    val phase: String,
    val inventoryItemId: String?,
    val caption: String,
    val mimeType: String,
    val createdAt: String
)

data class InventoryRoom(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val notes: String
)

enum class InventoryStatus(val serverKey: String, val label: String) {
    PENDING("pending", "Pending"),
    PACKED("packed", "Packed"),
    LOADED("loaded", "Loaded"),
    DELIVERED("delivered", "Delivered"),
    UNPACKED("unpacked", "Unpacked"),
    EXCEPTION("exception", "Exception"),
    MISSING("missing", "Missing");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.serverKey == raw } ?: PENDING
    }
}

data class InventoryItem(
    val id: String,
    val roomId: String?,
    val name: String,
    val category: String,
    val quantity: Int,
    val dimensions: String,
    val isFragile: Boolean,
    val isHeavy: Boolean,
    val packingMethod: String,
    val packingChargeRate: String?,
    val barcode: String,
    val status: InventoryStatus,
    val conditionBefore: String,
    val conditionAfter: String,
    val exceptionNote: String
)

data class PackingRule(
    val id: String,
    val name: String,
    val category: String,
    val unitPrice: String,
    val tax: String
)

data class JobInventoryState(
    val rooms: List<InventoryRoom>,
    val items: List<InventoryItem>,
    val packingRules: List<PackingRule>
)

data class JobAssignment(val staffId: String?, val assignee: String, val role: String)

/** Scheduled values come from the calendar; actual* values are recorded by the server clock. */
data class Job(
    val id: String,
    val title: String,
    val scheduledStart: Instant?,
    val scheduledEnd: Instant?,
    val dateOnly: Boolean,
    val serviceDate: String?,
    val bookingStatus: BookingStatus,
    val cancelled: Boolean,
    val state: JobState,
    val signoff: SignoffStatus,
    val reference: String?,
    val customerName: String?,
    val customerPhone: String?,
    val pickup: String?,
    val destination: String?,
    val truck: String?,
    val crewSize: Int?,
    val notes: String?,
    val assignees: List<String>,
    val actualStart: Instant?,
    val actualFinish: Instant?,
    val elapsedMinutes: Int,
    val breakMinutes: Int,
    val workingMinutes: Int,
    /** Calendar entry that is a notice/historical/duplicate note, not an operational job. */
    val notice: Boolean = false,
    val appScheduledStart: Instant? = null,
    val appScheduledEnd: Instant? = null,
    val vehicleId: String? = null,
    val staffIds: List<String> = emptyList(),
    val travelNotes: String = "",
    val dispatchNotes: String = "",
    val missingInfo: List<String> = emptyList(),
    val waitingMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val waitActive: Boolean = false,
    val updatedAt: String? = null,
    val crewEvents: List<CrewEventItem> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val conditionReports: List<ConditionReportItem> = emptyList(),
    val photos: List<JobPhotoMeta> = emptyList(),
    val assignmentDetails: List<JobAssignment>? = null
) {
    val effectiveStart: Instant? get() = appScheduledStart ?: scheduledStart
    val effectiveEnd: Instant? get() = appScheduledEnd ?: scheduledEnd
}

data class JobsSnapshot(
    val jobs: List<Job>,
    val todayConfirmed: Set<String>,
    val upcoming: Set<String>,
    val inProgress: Set<String>,
    val completed: Set<String>,
    val awaitingSignoff: Set<String>,
    val unconfirmed: Set<String>,
    val conflicts: List<Pair<String, String>>,
    val calendarConnected: Boolean,
    val lastSyncedAt: String?,
    val lastError: String?,
    val missingInfoIds: Set<String> = emptySet(),
    val twoWaySyncEnabled: Boolean = false
)

/** Seam between the ViewModel and the network so behaviour can be tested with a fake. */
interface JobsGateway {
    suspend fun snapshot(): JobsResult<JobsSnapshot>
    suspend fun detail(id: String): JobsResult<Job>
    suspend fun sync(): JobsResult<String>
    suspend fun act(id: String, action: String): JobsResult<String>
    suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String>
    suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String>
    suspend fun signoff(
        id: String,
        customerName: String,
        pngBase64: String,
        chargesAcknowledged: Boolean,
        damageNotes: String,
        outstandingItems: String
    ): JobsResult<String> = signoff(id, customerName, pngBase64)

    suspend fun skipSignoff(id: String, reason: String): JobsResult<String>
    suspend fun skipSignoff(
        id: String,
        reason: String,
        damageNotes: String,
        outstandingItems: String
    ): JobsResult<String> = skipSignoff(id, reason)
    suspend fun invoiceDraft(id: String): JobsResult<org.json.JSONObject>
    suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>>
    suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String>
    suspend fun saveInvoiceDraft(input: org.json.JSONObject, newCustomer: org.json.JSONObject?): JobsResult<String>

    suspend fun assignJob(
        id: String,
        staffAssignments: List<Triple<String?, String, String>>,
        vehicleId: String?,
        truck: String?,
        travelNotes: String,
        dispatchNotes: String
    ): JobsResult<String> = JobsResult.Failure("Not supported")

    suspend fun rescheduleJob(id: String, startIso: String, endIso: String, reason: String): JobsResult<String> =
        JobsResult.Failure("Not supported")

    suspend fun recordCrewEvent(
        id: String,
        memberName: String,
        eventType: String,
        customerAcknowledged: Boolean,
        acknowledgmentName: String,
        notes: String
    ): JobsResult<String> = JobsResult.Failure("Not supported")

    suspend fun fetchInventory(id: String): JobsResult<JobInventoryState> = JobsResult.Failure("Not supported")
    suspend fun addRoom(id: String, name: String): JobsResult<String> = JobsResult.Failure("Not supported")
    suspend fun saveInventoryItem(id: String, item: InventoryItem): JobsResult<String> = JobsResult.Failure("Not supported")
    suspend fun scanBarcode(id: String, barcode: String, status: InventoryStatus, note: String): JobsResult<String> =
        JobsResult.Failure("Not supported")

    suspend fun saveConditionReport(id: String, report: ConditionReportItem): JobsResult<String> =
        JobsResult.Failure("Not supported")

    suspend fun uploadPhoto(
        id: String,
        phase: String,
        caption: String,
        imageBase64: String,
        inventoryItemId: String? = null,
        idempotencyKey: String? = null
    ): JobsResult<String> = JobsResult.Failure("Not supported")
}

sealed interface JobsResult<out T> {
    data class Success<T>(val value: T) : JobsResult<T>
    data object AuthRequired : JobsResult<Nothing>
    data class Failure(val message: String, val connectionUnavailable: Boolean = false) : JobsResult<Nothing>
}
