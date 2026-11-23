package com.hfremovalsadelaide.invoices.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarView(val label: String) { AGENDA("Agenda"), DAY("Day"), WEEK("Week"), MONTH("Month") }

data class DraftReview(
    val jobId: String,
    val warnings: List<String>,
    val workingMinutes: Int,
    val customerName: String?,
    val summary: List<Pair<String, String>>,
    val input: JSONObject,
    val newCustomer: JSONObject?
)

data class JobsUiState(
    val loading: Boolean = false,
    val authRequired: Boolean = false,
    val snapshot: JobsSnapshot? = null,
    val view: CalendarView = CalendarView.AGENDA,
    val selectedDate: LocalDate = LocalDate.now(ZoneId.of("Australia/Adelaide")),
    val openJobId: String? = null,
    val busy: Boolean = false,
    val signoffJobId: String? = null,
    val draft: DraftReview? = null,
    val preferences: Map<NotificationCategory, Boolean> = NotificationCategory.entries.associateWith { true },
    val message: String? = null
)

class JobsViewModel(
    private val gateway: JobsGateway = JobsApi,
    private val onSnapshot: (JobsSnapshot) -> Unit = {},
    private val onPreferences: (Map<NotificationCategory, Boolean>) -> Unit = {}
) : ViewModel() {

    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.loading) return
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        _state.update { it.copy(loading = true) }
        when (val r = gateway.snapshot()) {
            is JobsResult.Success -> {
                _state.update { it.copy(loading = false, authRequired = false, snapshot = r.value) }
                onSnapshot(r.value)
            }
            JobsResult.AuthRequired -> _state.update { it.copy(loading = false, authRequired = true) }
            is JobsResult.Failure -> _state.update { it.copy(loading = false, message = r.message) }
        }
    }

    /** Pulls the calendar (read-only) and reloads jobs. */
    fun syncCalendar() = guarded {
        when (val r = gateway.sync()) {
            is JobsResult.Failure -> _state.update { it.copy(message = r.message) }
            JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
            is JobsResult.Success -> Unit
        }
        refresh()
    }

    fun act(jobId: String, action: String) = guarded {
        when (val r = gateway.act(jobId, action)) {
            is JobsResult.Success -> {
                refresh()
                if (action == "finish") _state.update { it.copy(signoffJobId = jobId) }
            }
            is JobsResult.Failure -> _state.update { it.copy(message = r.message) }
            JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
        }
    }

    fun correct(jobId: String, field: String, atIso: String, reason: String) {
        if (reason.isBlank()) return say("Enter a reason to correct a recorded time.")
        guarded { report(gateway.correct(jobId, field, atIso, reason.trim()), "Time corrected. The original is kept in the audit history.") }
    }

    fun submitSignoff(jobId: String, name: String, pngBase64: String) {
        if (name.isBlank()) return say("Enter the customer's name.")
        if (pngBase64.isBlank()) return say("The customer must sign in the box first.")
        guarded {
            val r = gateway.signoff(jobId, name.trim(), pngBase64)
            if (r is JobsResult.Success) _state.update { it.copy(signoffJobId = null) }
            report(r, "Sign-off saved.")
        }
    }

    fun skipSignoff(jobId: String, reason: String) = guarded {
        val r = gateway.skipSignoff(jobId, reason.trim())
        if (r is JobsResult.Success) _state.update { it.copy(signoffJobId = null) }
        report(r, "Sign-off skipped. The job is completed but not customer-signed.")
    }

    fun openSignoff(jobId: String) = _state.update { it.copy(signoffJobId = jobId) }
    fun dismissSignoff() = _state.update { it.copy(signoffJobId = null) }

    fun requestInvoiceDraft(jobId: String) = guarded {
        when (val r = gateway.invoiceDraft(jobId)) {
            is JobsResult.Success -> {
                val o = r.value
                val day = o.optJSONObject("input")?.optJSONArray("days")?.optJSONObject(0)
                val inp = o.optJSONObject("input") ?: JSONObject()
                val summary = buildList {
                    add("Service date" to inp.optString("serviceFrom"))
                    add("Pickup" to inp.optString("pickup").ifBlank { "-" })
                    add("Delivery" to inp.optString("delivery").ifBlank { "-" })
                    if (day != null) {
                        add("Recorded" to "${day.optString("start")} to ${day.optString("finish")}")
                        add("Break" to "${day.optInt("breakMinutes")} min")
                        add("Hourly rate" to "$" + day.optString("rate"))
                        add("Movers" to day.optInt("movers").toString())
                    }
                }
                val warnings = o.optJSONArray("warnings")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                _state.update {
                    it.copy(draft = DraftReview(jobId, warnings, o.optInt("workingMinutes"), o.optJSONObject("newCustomer")?.optString("name"), summary, inp, o.optJSONObject("newCustomer")))
                }
            }
            is JobsResult.Failure -> _state.update { it.copy(message = r.message) }
            JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
        }
    }

    /** Saves a DRAFT only. Invoices are never issued from here. */
    fun confirmInvoiceDraft() {
        val draft = _state.value.draft ?: return
        guarded {
            val r = gateway.saveInvoiceDraft(draft.input, draft.newCustomer)
            if (r is JobsResult.Success) _state.update { it.copy(draft = null) }
            report(r, "Draft invoice saved. Review and issue it from Invoices.")
        }
    }

    fun dismissDraft() = _state.update { it.copy(draft = null) }

    fun loadPreferences() {
        viewModelScope.launch {
            (gateway.notificationPreferences() as? JobsResult.Success)?.let { r ->
                _state.update { it.copy(preferences = r.value) }
                onPreferences(r.value)
            }
        }
    }

    fun setPreference(category: NotificationCategory, enabled: Boolean) {
        val next = _state.value.preferences + (category to enabled)
        _state.update { it.copy(preferences = next) }
        onPreferences(next)
        viewModelScope.launch { gateway.savePreferences(next) }
        _state.value.snapshot?.let(onSnapshot) // re-plan reminders with the new toggles
    }

    fun selectView(view: CalendarView) = _state.update { it.copy(view = view) }
    fun selectDate(date: LocalDate) = _state.update { it.copy(selectedDate = date) }
    fun openJob(id: String?) = _state.update { it.copy(openJobId = id) }
    fun messageShown() = _state.update { it.copy(message = null) }

    private fun say(text: String) = _state.update { it.copy(message = text) }

    private suspend fun report(r: JobsResult<String>, ok: String) {
        when (r) {
            is JobsResult.Success -> { say(ok); refresh() }
            is JobsResult.Failure -> say(r.message)
            JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
        }
    }

    /** One server action at a time: a second tap while busy is ignored, so nothing is recorded twice. */
    private fun guarded(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
