package com.hfremovalsadelaide.invoices.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfremovalsadelaide.invoices.offline.OfflineOpKind
import com.hfremovalsadelaide.invoices.offline.OfflineSyncQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class CalendarView(val label: String) {
    AGENDA("Agenda"),
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    DISPATCH("Dispatch")
}

enum class JobStatusFilter(val label: String) {
    ALL("All"),
    CONFIRMED("Confirmed"),
    PROVISIONAL("Provisional"),
    IN_PROGRESS("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}

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
    val searchQuery: String = "",
    val statusFilter: JobStatusFilter = JobStatusFilter.ALL,
    val openJobId: String? = null,
    val busy: Boolean = false,
    val signoffJobId: String? = null,
    val draft: DraftReview? = null,
    val preferences: Map<NotificationCategory, Boolean> = NotificationCategory.entries.associateWith { true },
    val isFromOfflineCache: Boolean = false,
    val inventory: JobInventoryState? = null,
    val inventoryLoading: Boolean = false,
    val scheduleConflict: ScheduleConflictPrompt? = null,
    val dispatchFilter: DispatchFilter = DispatchFilter(),
    val message: String? = null
) {
    fun filteredJobs(): List<Job> {
        val raw = snapshot?.jobs.orEmpty()
        val q = searchQuery.trim().lowercase()
        return raw.filter { job ->
            val statusMatches = when (statusFilter) {
                JobStatusFilter.ALL -> !job.cancelled
                JobStatusFilter.CONFIRMED -> !job.cancelled && job.bookingStatus == BookingStatus.CONFIRMED
                JobStatusFilter.PROVISIONAL -> !job.cancelled && job.bookingStatus != BookingStatus.CONFIRMED
                JobStatusFilter.IN_PROGRESS -> !job.cancelled && (job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED)
                JobStatusFilter.COMPLETED -> !job.cancelled && job.state == JobState.COMPLETED
                JobStatusFilter.CANCELLED -> job.cancelled
            }
            val queryMatches = q.isBlank() ||
                job.title.lowercase().contains(q) ||
                (job.customerName?.lowercase()?.contains(q) == true) ||
                (job.pickup?.lowercase()?.contains(q) == true) ||
                (job.destination?.lowercase()?.contains(q) == true) ||
                (job.reference?.lowercase()?.contains(q) == true) ||
                (job.serviceDate?.lowercase()?.contains(q) == true) ||
                (job.truck?.lowercase()?.contains(q) == true) ||
                job.assignees.any { it.lowercase().contains(q) }
            statusMatches && queryMatches
        }
    }
}

class JobsViewModel(
    private val gateway: JobsGateway = JobsApi,
    private val onSnapshot: (JobsSnapshot) -> Unit = {},
    private val onPreferences: (Map<NotificationCategory, Boolean>) -> Unit = {},
    private val syncQueue: OfflineSyncQueue? = null,
    private val cacheRead: () -> String? = { null },
    private val cacheWrite: (String) -> Unit = {},
    private val isNetworkAvailable: () -> Boolean = { true },
    private val scheduleOfflineSync: () -> Unit = {}
) : ViewModel() {

    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    fun resetAccountState() {
        viewModelScope.coroutineContext.cancelChildren()
        _state.value = JobsUiState()
    }

    fun load() {
        if (_state.value.loading) return
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        _state.update { it.copy(loading = true) }
        if (!isNetworkAvailable()) {
            val cached = JobSnapshotCodec.decode(cacheRead())
            if (cached != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        authRequired = false,
                        snapshot = cached,
                        isFromOfflineCache = true
                    )
                }
                return
            }
        }
        when (val r = gateway.snapshot()) {
            is JobsResult.Success -> {
                cacheWrite(JobSnapshotCodec.encode(r.value))
                _state.update {
                    it.copy(
                        loading = false,
                        authRequired = false,
                        snapshot = r.value,
                        isFromOfflineCache = false
                    )
                }
                onSnapshot(r.value)
            }
            JobsResult.AuthRequired -> _state.update { it.copy(loading = false, authRequired = true) }
            is JobsResult.Failure -> {
                val cached = JobSnapshotCodec.decode(cacheRead())
                _state.update {
                    it.copy(
                        loading = false,
                        snapshot = it.snapshot ?: cached,
                        isFromOfflineCache = (it.snapshot ?: cached) != null,
                        message = r.message
                    )
                }
            }
        }
    }

    fun refreshJobDetail(jobId: String) {
        viewModelScope.launch {
            when (val r = gateway.detail(jobId)) {
                is JobsResult.Success -> mergeJob(r.value)
                JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
                is JobsResult.Failure -> _state.update { it.copy(message = r.message) }
            }
        }
    }

    private fun mergeJob(updated: Job) {
        _state.update { st ->
            val snap = st.snapshot ?: return@update st
            val exists = snap.jobs.any { it.id == updated.id }
            val nextJobs = if (exists) {
                snap.jobs.map { if (it.id == updated.id) updated else it }
            } else {
                listOf(updated) + snap.jobs
            }
            st.copy(snapshot = snap.copy(jobs = nextJobs))
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
        val currentJob = _state.value.snapshot?.jobs?.firstOrNull { it.id == jobId }
        if (!isNetworkAvailable() && syncQueue != null && action in OFFLINE_SAFE_ACTIONS) {
            enqueueTimeAction(jobId, action, currentJob)
            return@guarded
        }
        when (val r = gateway.act(jobId, action)) {
            is JobsResult.Success -> {
                refresh()
                refreshJobDetail(jobId)
                if (action == "finish") _state.update { it.copy(signoffJobId = jobId) }
            }
            is JobsResult.Failure -> {
                if (syncQueue != null && action in OFFLINE_SAFE_ACTIONS && isConnectionError(r.message)) {
                    enqueueTimeAction(jobId, action, currentJob)
                } else {
                    _state.update { it.copy(message = r.message) }
                }
            }
            JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
        }
    }

    private fun enqueueTimeAction(jobId: String, action: String, job: Job?) {
        syncQueue?.enqueue(
            kind = OfflineOpKind.JOB_TIME_ACTION,
            jobId = jobId,
            label = "${job?.title?.take(28).orEmpty().ifBlank { "Job" }} · $action",
            payload = JSONObject().put("action", action),
            expectedUpdatedAt = job?.updatedAt
        )
        scheduleOfflineSync()
        val nextState = when (action) {
            "start", "resume" -> JobState.IN_PROGRESS
            "pause" -> JobState.PAUSED
            "finish" -> JobState.COMPLETED
            else -> job?.state
        }
        if (job != null && nextState != null) {
            val nextWait = when (action) {
                "wait_start" -> true
                "wait_end" -> false
                else -> job.waitActive
            }
            mergeJob(job.copy(state = nextState, waitActive = nextWait))
        }
        if (action == "finish") _state.update { it.copy(signoffJobId = jobId) }
        say("Saved offline ($action). Will sync automatically when back online.")
    }

    fun correct(jobId: String, field: String, atIso: String, reason: String) {
        if (reason.isBlank()) return say("Enter a reason to correct a recorded time.")
        guarded {
            report(
                gateway.correct(jobId, field, atIso, reason.trim()),
                "Time corrected. The original is kept in the audit history."
            )
            refreshJobDetail(jobId)
        }
    }

    fun assignJob(
        jobId: String,
        staffAssignments: List<Triple<String?, String, String>>,
        vehicleId: String?,
        truck: String?,
        travelNotes: String,
        dispatchNotes: String,
        overrideReason: String? = null
    ) = guarded {
        val result = gateway.assignJob(jobId, staffAssignments, vehicleId, truck, travelNotes.trim(), dispatchNotes.trim(), overrideReason)
        val pending = PendingScheduleAction.Assign(staffAssignments, vehicleId, truck, travelNotes, dispatchNotes)
        if (promptIfConflict(result, jobId, pending)) return@guarded
        report(result, "Crew and vehicle assignment updated.")
        refreshJobDetail(jobId)
    }

    fun rescheduleJob(jobId: String, startIso: String, endIso: String, reason: String, overrideReason: String? = null) {
        if (startIso.isBlank() || endIso.isBlank()) return say("Enter start and end times.")
        guarded {
            val result = gateway.rescheduleJob(jobId, startIso, endIso, reason.trim(), overrideReason)
            if (promptIfConflict(result, jobId, PendingScheduleAction.Reschedule(startIso, endIso, reason))) return@guarded
            report(result, "Job rescheduled in dispatch board.")
            refreshJobDetail(jobId)
        }
    }

    /** A server-detected clash is not an error message: it opens a prompt that an authorised dispatcher can override. */
    private fun promptIfConflict(result: JobsResult<String>, jobId: String, pending: PendingScheduleAction): Boolean {
        if (result !is JobsResult.Failure || result.conflicts.isEmpty()) return false
        _state.update { it.copy(scheduleConflict = ScheduleConflictPrompt(jobId, result.conflicts, pending)) }
        return true
    }

    /** Resends the refused change with the dispatcher's written reason; the server audits it and checks the permission. */
    fun confirmScheduleOverride(reason: String) {
        val prompt = _state.value.scheduleConflict ?: return
        val trimmed = reason.trim()
        if (trimmed.length < OVERRIDE_REASON_MIN) return say("Enter a reason of at least $OVERRIDE_REASON_MIN characters.")
        _state.update { it.copy(scheduleConflict = null) }
        when (val action = prompt.retry) {
            is PendingScheduleAction.Assign -> assignJob(
                prompt.jobId, action.staffAssignments, action.vehicleId, action.truck, action.travelNotes, action.dispatchNotes, trimmed
            )
            is PendingScheduleAction.Reschedule -> rescheduleJob(prompt.jobId, action.startIso, action.endIso, action.reason, trimmed)
        }
    }

    fun dismissScheduleConflict() = _state.update { it.copy(scheduleConflict = null) }

    fun setDispatchFilter(filter: DispatchFilter) = _state.update { it.copy(dispatchFilter = filter) }

    fun setPriority(jobId: String, priority: JobPriority) = guarded {
        report(gateway.setPriority(jobId, priority), "Priority set to ${priority.label}.")
    }

    fun recordCrewEvent(
        jobId: String,
        memberName: String,
        eventType: String,
        customerAcknowledged: Boolean,
        acknowledgmentName: String,
        notes: String
    ) {
        if (memberName.isBlank()) return say("Enter the crew member's name.")
        guarded {
            report(
                gateway.recordCrewEvent(jobId, memberName.trim(), eventType, customerAcknowledged, acknowledgmentName.trim(), notes.trim()),
                "Crew log updated."
            )
            refreshJobDetail(jobId)
        }
    }

    fun loadInventory(jobId: String) {
        viewModelScope.launch {
            _state.update { it.copy(inventoryLoading = true) }
            when (val r = gateway.fetchInventory(jobId)) {
                is JobsResult.Success -> _state.update { it.copy(inventoryLoading = false, inventory = r.value) }
                JobsResult.AuthRequired -> _state.update { it.copy(inventoryLoading = false, authRequired = true) }
                is JobsResult.Failure -> _state.update { it.copy(inventoryLoading = false, message = r.message) }
            }
        }
    }

    fun addRoom(jobId: String, name: String) {
        if (name.isBlank()) return say("Enter a room or zone name.")
        guarded {
            when (val r = gateway.addRoom(jobId, name.trim())) {
                is JobsResult.Success -> {
                    say("Room added.")
                    loadInventory(jobId)
                }
                is JobsResult.Failure -> say(r.message)
                JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
            }
        }
    }

    fun saveInventoryItem(jobId: String, item: InventoryItem) {
        if (item.name.isBlank()) return say("Enter an item description.")
        guarded {
            when (val r = gateway.saveInventoryItem(jobId, item)) {
                is JobsResult.Success -> {
                    say("Inventory item saved.")
                    loadInventory(jobId)
                }
                is JobsResult.Failure -> say(r.message)
                JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
            }
        }
    }

    fun scanBarcode(jobId: String, barcode: String, status: InventoryStatus, note: String = "") {
        val code = barcode.trim()
        if (code.length < 2) return say("Enter or scan a valid barcode / QR code.")
        guarded {
            val payload = JSONObject()
                .put("barcode", code)
                .put("status", status.serverKey)
                .put("exceptionNote", note.trim())
            if (!isNetworkAvailable() && syncQueue != null) {
                syncQueue.enqueue(OfflineOpKind.INVENTORY_SCAN, jobId, "Scan $code -> ${status.label}", payload)
                scheduleOfflineSync()
                say("Scan saved offline. Will sync when online.")
                return@guarded
            }
            when (val r = gateway.scanBarcode(jobId, code, status, note.trim())) {
                is JobsResult.Success -> {
                    say("Carton $code updated to ${status.label}.")
                    loadInventory(jobId)
                }
                is JobsResult.Failure -> {
                    if (syncQueue != null && isConnectionError(r.message)) {
                        syncQueue.enqueue(OfflineOpKind.INVENTORY_SCAN, jobId, "Scan $code -> ${status.label}", payload)
                        scheduleOfflineSync()
                        say("Scan saved offline. Will sync when online.")
                    } else {
                        say(r.message)
                    }
                }
                JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
            }
        }
    }

    fun saveConditionReport(jobId: String, report: ConditionReportItem) = guarded {
        val payload = JSONObject()
            .put("phase", report.phase)
            .put("accessConditions", report.accessConditions)
            .put("existingDamage", report.existingDamage)
            .put("delaysOrIssues", report.delaysOrIssues)
            .put("additionalServices", report.additionalServices)
            .put("checklist", JSONObject().apply { report.checklist.forEach { (k, v) -> put(k, v) } })
            .put("customerAcknowledged", report.customerAcknowledged)
            .put("acknowledgmentName", report.acknowledgmentName)
            .put("notes", report.notes)
        if (!isNetworkAvailable() && syncQueue != null) {
            syncQueue.enqueue(OfflineOpKind.CONDITION_REPORT, jobId, "${report.phase} condition report", payload)
            scheduleOfflineSync()
            say("Condition report saved offline. Will sync automatically.")
            return@guarded
        }
        when (val r = gateway.saveConditionReport(jobId, report)) {
            is JobsResult.Success -> {
                say("Condition report (${report.phase}) saved.")
                refreshJobDetail(jobId)
            }
            is JobsResult.Failure -> {
                if (syncQueue != null && isConnectionError(r.message)) {
                    syncQueue.enqueue(OfflineOpKind.CONDITION_REPORT, jobId, "${report.phase} condition report", payload)
                    scheduleOfflineSync()
                    say("Condition report saved offline. Will sync automatically.")
                } else {
                    say(r.message)
                }
            }
            JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
        }
    }

    fun uploadPhoto(jobId: String, phase: String, caption: String, imageBase64: String, inventoryItemId: String? = null) = guarded {
        val idempotencyKey = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("phase", phase)
            .put("inventoryItemId", inventoryItemId ?: JSONObject.NULL)
            .put("caption", caption.trim())
            .put("imageBase64", imageBase64)
        if (!isNetworkAvailable() && syncQueue != null) {
            syncQueue.enqueue(OfflineOpKind.JOB_PHOTO, jobId, "Photo ($phase)", payload, idempotencyKey = idempotencyKey)
            scheduleOfflineSync()
            say("Photo queued offline. Will upload when connected.")
            return@guarded
        }
        when (val r = gateway.uploadPhoto(jobId, phase, caption.trim(), imageBase64, inventoryItemId, idempotencyKey)) {
            is JobsResult.Success -> {
                say("Photo attached securely.")
                refreshJobDetail(jobId)
            }
            is JobsResult.Failure -> {
                if (syncQueue != null && isConnectionError(r.message)) {
                    syncQueue.enqueue(OfflineOpKind.JOB_PHOTO, jobId, "Photo ($phase)", payload, idempotencyKey = idempotencyKey)
                    scheduleOfflineSync()
                    say("Photo queued offline. Will upload when connected.")
                } else {
                    say(r.message)
                }
            }
            JobsResult.AuthRequired -> _state.update { it.copy(authRequired = true) }
        }
    }

    fun submitSignoff(
        jobId: String,
        name: String,
        pngBase64: String,
        chargesAcknowledged: Boolean = false,
        damageNotes: String = "",
        outstandingItems: String = ""
    ) {
        if (name.isBlank()) return say("Enter the customer's name.")
        if (pngBase64.isBlank()) return say("The customer must sign in the box first.")
        guarded {
            val r = gateway.signoff(
                jobId,
                name.trim(),
                pngBase64,
                chargesAcknowledged = chargesAcknowledged,
                damageNotes = damageNotes.trim(),
                outstandingItems = outstandingItems.trim()
            )
            if (r is JobsResult.Success) _state.update { it.copy(signoffJobId = null) }
            report(r, "Sign-off saved.")
        }
    }

    fun skipSignoff(
        jobId: String,
        reason: String,
        damageNotes: String = "",
        outstandingItems: String = ""
    ) = guarded {
        val r = gateway.skipSignoff(jobId, reason.trim(), damageNotes.trim(), outstandingItems.trim())
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
                    it.copy(
                        draft = DraftReview(
                            jobId,
                            warnings,
                            o.optInt("workingMinutes"),
                            o.optJSONObject("newCustomer")?.optString("name"),
                            summary,
                            inp,
                            o.optJSONObject("newCustomer")
                        )
                    )
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
        _state.value.snapshot?.let(onSnapshot)
    }

    fun selectView(view: CalendarView) = _state.update { it.copy(view = view) }
    fun selectDate(date: LocalDate) = _state.update { it.copy(selectedDate = date) }
    fun onSearchQueryChanged(query: String) = _state.update { it.copy(searchQuery = query) }
    fun onStatusFilterSelected(filter: JobStatusFilter) = _state.update { it.copy(statusFilter = filter) }
    fun openJob(id: String?) {
        _state.update { it.copy(openJobId = id, inventory = if (id == null) null else it.inventory) }
        if (id != null) {
            refreshJobDetail(id)
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun say(text: String) = _state.update { it.copy(message = text) }

    private fun isConnectionError(message: String): Boolean =
        message.contains("connection", ignoreCase = true) ||
            message.contains("network", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true)

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

    companion object {
        private val OFFLINE_SAFE_ACTIONS = setOf("start", "pause", "resume", "finish", "wait_start", "wait_end")
    }
}
