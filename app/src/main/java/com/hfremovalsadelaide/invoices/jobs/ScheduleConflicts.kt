package com.hfremovalsadelaide.invoices.jobs

import org.json.JSONObject

/** One clash the server found when a crew, truck or time change was requested (backend lib/jobs/conflicts.ts). */
data class ScheduleConflictInfo(
    val kind: String,
    val subject: String,
    val otherJobId: String? = null,
    val otherJobTitle: String? = null,
    val from: String? = null,
    val to: String? = null,
    val reason: String? = null
)

/** What to resend, with a written reason, if an authorised dispatcher chooses to override. */
sealed interface PendingScheduleAction {
    data class Assign(
        val staffAssignments: List<Triple<String?, String, String>>,
        val vehicleId: String?,
        val truck: String?,
        val travelNotes: String,
        val dispatchNotes: String
    ) : PendingScheduleAction

    data class Reschedule(val startIso: String, val endIso: String, val reason: String) : PendingScheduleAction
}

data class ScheduleConflictPrompt(
    val jobId: String,
    val conflicts: List<ScheduleConflictInfo>,
    val retry: PendingScheduleAction
)

const val OVERRIDE_REASON_MIN = 5

/** Empty unless the body is the backend's schedule_conflict 409; any other error stays an ordinary failure. */
fun parseScheduleConflicts(body: String): List<ScheduleConflictInfo> = runCatching {
    val root = JSONObject(body)
    if (root.optString("code") != "schedule_conflict") return@runCatching emptyList()
    val arr = root.optJSONArray("conflicts") ?: return@runCatching emptyList()
    List(arr.length()) { i ->
        val c = arr.getJSONObject(i)
        fun text(key: String) = if (c.isNull(key)) null else c.optString(key).ifBlank { null }
        ScheduleConflictInfo(
            kind = c.optString("kind"),
            subject = c.optString("subject"),
            otherJobId = text("otherJobId"),
            otherJobTitle = text("otherJobTitle"),
            from = text("from"),
            to = text("to"),
            reason = text("reason")
        )
    }
}.getOrDefault(emptyList())

fun describeConflict(c: ScheduleConflictInfo): String = when (c.kind) {
    "crew", "vehicle" -> "${c.subject} is already on \"${c.otherJobTitle ?: "another job"}\" at the same time."
    "staff_unavailable", "vehicle_unavailable" -> "${c.subject} is unavailable: ${c.reason ?: "${c.from.orEmpty()} to ${c.to.orEmpty()}"}"
    else -> c.reason ?: c.subject
}
