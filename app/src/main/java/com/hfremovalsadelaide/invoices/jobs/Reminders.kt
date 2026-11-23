package com.hfremovalsadelaide.invoices.jobs

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Reminder(val key: String, val jobId: String, val category: NotificationCategory, val at: Instant)
data class JobChange(val jobId: String, val category: NotificationCategory)

object ReminderPlanner {
    private val lead = listOf(
        NotificationCategory.UPCOMING_24H to Duration.ofHours(24),
        NotificationCategory.UPCOMING_1H to Duration.ofHours(1),
        NotificationCategory.STARTING_SOON_15M to Duration.ofMinutes(15),
        NotificationCategory.SCHEDULED_START to Duration.ZERO
    )

    /**
     * Future reminders only. The key includes the scheduled start, so an unchanged job always maps to the
     * same key (re-syncs, restarts and reboots cannot duplicate it) and a rescheduled job gets new keys.
     */
    fun plan(jobs: List<Job>, enabled: Map<NotificationCategory, Boolean>, now: Instant): List<Reminder> =
        jobs.flatMap { job ->
            val start = job.scheduledStart
            if (job.cancelled || job.dateOnly || start == null) return@flatMap emptyList()
            val out = mutableListOf<Reminder>()
            if (job.state == JobState.SCHEDULED) {
                lead.forEach { (category, before) -> out += Reminder(key(job, category, start), job.id, category, start.minus(before)) }
            }
            val end = job.scheduledEnd
            if ((job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED) && end != null) {
                out += Reminder(key(job, NotificationCategory.RUNNING_LATE, start), job.id, NotificationCategory.RUNNING_LATE, end)
            }
            out.filter { enabled[it.category] != false && it.at.isAfter(now) }
        }

    private fun key(job: Job, category: NotificationCategory, start: Instant) = "${job.id}|${category.serverKey}|${start.epochSecond}"
}

/** What we remember about a job between syncs so changes can be detected without spamming. */
data class JobFingerprint(val start: Instant?, val cancelled: Boolean, val state: JobState, val assignees: List<String>) {
    companion object {
        fun of(job: Job) = JobFingerprint(job.scheduledStart, job.cancelled, job.state, job.assignees.sorted())
    }
}

object ChangeDetector {
    /** Jobs absent from [previous] are new and never produce a change (the first sync stays silent). */
    fun diff(previous: Map<String, JobFingerprint>, current: List<Job>): List<JobChange> = current.flatMap { job ->
        val old = previous[job.id] ?: return@flatMap emptyList()
        val now = JobFingerprint.of(job)
        buildList {
            if ((now.cancelled && !old.cancelled) || (!now.cancelled && now.start != old.start)) {
                add(JobChange(job.id, NotificationCategory.RESCHEDULED_OR_CANCELLED))
            }
            if (now.state == JobState.COMPLETED && old.state != JobState.COMPLETED) {
                add(JobChange(job.id, NotificationCategory.COMPLETED))
                if (job.signoff == SignoffStatus.NONE) add(JobChange(job.id, NotificationCategory.SIGNOFF_PENDING))
            }
            if (now.assignees != old.assignees) add(JobChange(job.id, NotificationCategory.ASSIGNMENT_CHANGED))
        }
    }
}

/** Customer phone numbers and addresses are never put in any notification. */
object ReminderText {
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Australia/Adelaide"))

    fun publicVersion(category: NotificationCategory) = when (category) {
        NotificationCategory.COMPLETED, NotificationCategory.SIGNOFF_PENDING -> "Job update"
        NotificationCategory.RESCHEDULED_OR_CANCELLED, NotificationCategory.ASSIGNMENT_CHANGED -> "Booking change"
        else -> "Job reminder"
    }

    fun body(category: NotificationCategory, job: Job): String {
        val at = job.scheduledStart?.let { time.format(it) }
        return when (category) {
            NotificationCategory.UPCOMING_24H -> "Tomorrow" + (at?.let { " at $it" } ?: "")
            NotificationCategory.UPCOMING_1H -> "Starts in 1 hour" + (at?.let { " ($it)" } ?: "")
            NotificationCategory.STARTING_SOON_15M -> "Starts in 15 minutes" + (at?.let { " ($it)" } ?: "")
            NotificationCategory.SCHEDULED_START -> "Scheduled start time reached"
            NotificationCategory.RUNNING_LATE -> "Running past the scheduled finish"
            NotificationCategory.COMPLETED -> "Job completed"
            NotificationCategory.SIGNOFF_PENDING -> "Customer sign-off is still pending"
            NotificationCategory.RESCHEDULED_OR_CANCELLED -> if (job.cancelled) "Booking cancelled in the calendar" else "Booking time changed"
            NotificationCategory.ASSIGNMENT_CHANGED -> "Crew assignment changed"
        }
    }
}
