package com.hfremovalsadelaide.invoices.jobs

import org.json.JSONObject

/** The server's derived booking lifecycle (lib/bookings/status.ts). Order matches the business flow. */
enum class LifecycleStatus(val server: String) {
    NEW_ENQUIRY("new_enquiry"),
    QUOTATION_REQUIRED("quotation_required"),
    QUOTE_SENT("quote_sent"),
    AWAITING_CONFIRMATION("awaiting_confirmation"),
    CONFIRMED("confirmed"),
    ASSIGNED("assigned"),
    TRAVELLING("travelling"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    AWAITING_INVOICE("awaiting_invoice"),
    INVOICED("invoiced"),
    PAID("paid"),
    CANCELLED("cancelled");

    companion object {
        fun from(value: String?): LifecycleStatus? = entries.firstOrNull { it.server == value }
    }
}

data class BookingLifecycle(val status: LifecycleStatus, val label: String, val cancellationReason: String?) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", status.server)
        .put("label", label)
        .put("cancellationReason", cancellationReason ?: JSONObject.NULL)

    companion object {
        /** Null when absent or when the status is one this app version does not know. */
        fun fromJson(o: JSONObject?): BookingLifecycle? {
            if (o == null) return null
            val status = LifecycleStatus.from(o.optString("status", "")) ?: return null
            val label = o.optString("label", "").ifBlank { return null }
            val reason = if (o.isNull("cancellationReason")) null else o.optString("cancellationReason").ifBlank { null }
            return BookingLifecycle(status, label, reason)
        }
    }
}

enum class PillTone { DANGER, ACTIVE, SECONDARY, SUCCESS, PRIMARY, NEUTRAL }

/** What the job status pill says and how it is coloured; kept pure so it can be unit tested. */
data class JobStatusPill(val text: String, val tone: PillTone) {
    companion object {
        fun forJob(job: Job): JobStatusPill {
            val lifecycle = job.lifecycle ?: return legacy(job)
            val text = if (lifecycle.status == LifecycleStatus.IN_PROGRESS && job.state == JobState.PAUSED) "${lifecycle.label} · paused" else lifecycle.label
            val tone = when (lifecycle.status) {
                LifecycleStatus.CANCELLED -> PillTone.DANGER
                LifecycleStatus.TRAVELLING, LifecycleStatus.IN_PROGRESS -> PillTone.ACTIVE
                LifecycleStatus.CONFIRMED, LifecycleStatus.ASSIGNED -> PillTone.SECONDARY
                LifecycleStatus.PAID -> PillTone.SUCCESS
                LifecycleStatus.COMPLETED, LifecycleStatus.AWAITING_INVOICE, LifecycleStatus.INVOICED -> PillTone.PRIMARY
                LifecycleStatus.NEW_ENQUIRY, LifecycleStatus.QUOTATION_REQUIRED, LifecycleStatus.QUOTE_SENT,
                LifecycleStatus.AWAITING_CONFIRMATION -> PillTone.NEUTRAL
            }
            return JobStatusPill(text, tone)
        }

        /** The wording crews and older backends have always seen. */
        private fun legacy(job: Job): JobStatusPill = when {
            job.cancelled -> JobStatusPill("Cancelled", PillTone.DANGER)
            job.state == JobState.IN_PROGRESS -> JobStatusPill("In progress", PillTone.ACTIVE)
            job.state == JobState.PAUSED -> JobStatusPill("Paused", PillTone.SECONDARY)
            job.state == JobState.COMPLETED && job.signoff == SignoffStatus.SIGNED -> JobStatusPill("Signed", PillTone.SUCCESS)
            job.state == JobState.COMPLETED && job.signoff == SignoffStatus.SKIPPED -> JobStatusPill("Completed · not signed", PillTone.PRIMARY)
            job.state == JobState.COMPLETED -> JobStatusPill("Awaiting sign-off", PillTone.PRIMARY)
            else -> JobStatusPill("Scheduled", PillTone.NEUTRAL)
        }
    }
}

object BookingCancel {
    const val MAX_REASON = 500

    /** Mirrors the server rule: only a booking that has not started and is not already cancelled. */
    fun canOffer(job: Job, hasPermission: Boolean): Boolean =
        hasPermission && job.state == JobState.SCHEDULED && !job.cancelled &&
            job.lifecycle?.status != LifecycleStatus.CANCELLED
}
