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
    ASSIGNMENT_CHANGED("assignment_changed", "Assigned-job changes")
}

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
    val notice: Boolean = false
)

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
    val lastError: String?
)

/** Seam between the ViewModel and the network so behaviour can be tested with a fake. */
interface JobsGateway {
    suspend fun snapshot(): JobsResult<JobsSnapshot>
    suspend fun detail(id: String): JobsResult<Job>
    suspend fun sync(): JobsResult<String>
    suspend fun act(id: String, action: String): JobsResult<String>
    suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String>
    suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String>
    suspend fun skipSignoff(id: String, reason: String): JobsResult<String>
    suspend fun invoiceDraft(id: String): JobsResult<org.json.JSONObject>
    suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>>
    suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String>
    suspend fun saveInvoiceDraft(input: org.json.JSONObject, newCustomer: org.json.JSONObject?): JobsResult<String>
}

sealed interface JobsResult<out T> {
    data class Success<T>(val value: T) : JobsResult<T>
    data object AuthRequired : JobsResult<Nothing>
    data class Failure(val message: String) : JobsResult<Nothing>
}
