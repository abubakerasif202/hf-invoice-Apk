package com.hfremovalsadelaide.invoices.ui.customers

/**
 * Quote stages as the backend stores them (lib/crm.ts QUOTE_STAGES). The app sends and compares
 * [key]; only [label] is shown. Sending a label is rejected by the server with a 400.
 */
enum class QuoteStage(val key: String, val label: String) {
    NEW_ENQUIRY("new_enquiry", "New Enquiry"),
    QUOTE_PREPARED("quote_prepared", "Quote Prepared"),
    QUOTE_SENT("quote_sent", "Quote Sent"),
    ACCEPTED("accepted", "Accepted"),
    BOOKING_CONFIRMED("booking_confirmed", "Booking Confirmed"),
    JOB_COMPLETED("job_completed", "Job Completed"),
    INVOICED("invoiced", "Invoiced"),
    PAID("paid", "Paid"),
    DECLINED("declined", "Declined"),
    ARCHIVED("archived", "Archived");

    companion object {
        val INITIAL = NEW_ENQUIRY

        /** The forward pipeline shown as filters; declined and archived are end states outside it. */
        val pipeline: List<QuoteStage> = listOf(
            NEW_ENQUIRY, QUOTE_PREPARED, QUOTE_SENT, ACCEPTED, BOOKING_CONFIRMED, JOB_COMPLETED, INVOICED, PAID
        )

        fun fromKey(key: String?): QuoteStage? = entries.firstOrNull { it.key == key }

        fun labelFor(key: String): String = fromKey(key)?.label ?: key

        fun next(key: String): QuoteStage? {
            val idx = pipeline.indexOf(fromKey(key) ?: return null)
            return if (idx in 0 until pipeline.lastIndex) pipeline[idx + 1] else null
        }
    }
}

/** Template kinds accepted by POST /api/crm render_template, with their button labels. */
val QUOTE_TEMPLATE_KINDS: List<Pair<String, String>> = listOf(
    "quote" to "Quote",
    "quote_revision" to "Revision",
    "quote_followup" to "Quote Follow-up",
    "booking_confirmation" to "Confirmation",
    "deposit_receipt" to "Deposit Receipt",
    "move_day_dispatch" to "Crew Dispatch",
    "reminder" to "Reminder",
    "post_move_followup" to "Follow-up",
    "payment_request" to "Payment Request"
)

val ACCEPTANCE_METHODS: List<Pair<String, String>> = listOf(
    "email" to "Email approval",
    "portal" to "Customer portal",
    "phone" to "Phone confirmation",
    "signed_pdf" to "Signed PDF",
    "in_person" to "In person"
)
