package com.hfremovalsadelaide.invoices.ui.jobs

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.jobs.BookingCancel
import com.hfremovalsadelaide.invoices.jobs.DraftReview
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.jobs.LifecycleStatus
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.jobs.JobState
import com.hfremovalsadelaide.invoices.jobs.JourneyStatus
import com.hfremovalsadelaide.invoices.jobs.MovingDay
import com.hfremovalsadelaide.invoices.jobs.SignoffStatus
import com.hfremovalsadelaide.invoices.jobs.StopDraft
import com.hfremovalsadelaide.invoices.jobs.StopKind
import com.hfremovalsadelaide.invoices.jobs.StopStatus
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import java.time.Duration
import java.time.Instant
import java.util.Timer
import java.util.TimerTask

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobDetailScreen(
    job: Job,
    busy: Boolean,
    draft: DraftReview?,
    onBack: () -> Unit,
    onAct: (String) -> Unit,
    onCorrect: (field: String, atIso: String, reason: String) -> Unit,
    onOpenSignoff: () -> Unit,
    onDownloadCompletionPdf: (includePhotos: Boolean) -> Unit,
    onCreateInvoice: () -> Unit,
    onConfirmDraft: () -> Unit,
    onDismissDraft: () -> Unit,
    permissions: Set<String>,
    canDispatch: Boolean = false,
    onOpenAssign: () -> Unit = {},
    onOpenReschedule: () -> Unit = {},
    onOpenInventory: () -> Unit = {},
    onOpenCondition: () -> Unit = {},
    onRecordCrewEvent: (memberName: String, eventType: String, ack: Boolean, ackName: String, notes: String, at: Instant?) -> Unit = { _, _, _, _, _, _ -> },
    onStopEvent: (stopId: String, event: String) -> Unit = { _, _ -> },
    onSaveStops: (List<StopDraft>) -> Unit = {},
    onCancelBooking: (reason: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var editingStops by remember { mutableStateOf(false) }
    var cancellingBooking by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf(false) }
    var recordingCrewEvent by remember { mutableStateOf(false) }

    var nowEpoch by remember { mutableLongStateOf(Instant.now().epochSecond) }
    DisposableEffect(job.state) {
        if (job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED) {
            val timer = Timer("hf-job-ticker", true)
            timer.scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        nowEpoch = Instant.now().epochSecond
                    }
                },
                15_000L,
                15_000L
            )
            onDispose { timer.cancel() }
        } else {
            onDispose { }
        }
    }

    val liveElapsedMinutes = remember(job, nowEpoch) {
        if (job.state == JobState.IN_PROGRESS && job.actualStart != null) {
            Duration.between(job.actualStart, Instant.ofEpochSecond(nowEpoch)).toMinutes().toInt().coerceAtLeast(job.elapsedMinutes)
        } else job.elapsedMinutes
    }

    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(ForestGreen).statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to jobs", tint = PureWhite) }
            Text(job.title.ifBlank { "Booking" }, color = PureWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (job.lifecycle != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Booking status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusPill(job)
                }
            }
            if (job.notice) Banner("This calendar entry is a notice or note, not a job, so it cannot be started.", false)
            val cancelReason = job.lifecycle?.takeIf { it.status == LifecycleStatus.CANCELLED }?.cancellationReason
            if (cancelReason != null) {
                Banner("Booking cancelled: $cancelReason. Recorded times are kept.", true)
            } else if (job.cancelled) {
                Banner("Cancelled in the calendar. Recorded times are kept.", true)
            }
            if (job.bookingStatus != com.hfremovalsadelaide.invoices.jobs.BookingStatus.CONFIRMED) {
                Banner("Booking details are not marked confirmed. Check with the customer before relying on them.", false)
            }
            if (job.missingInfo.isNotEmpty()) {
                Banner("Missing dispatch details: ${job.missingInfo.joinToString()}", false)
            }

            ActionArea(
                job = job,
                busy = busy,
                permissions = permissions,
                onAct = onAct,
                onConfirmFinish = { confirmFinish = true },
                onOpenSignoff = onOpenSignoff,
                onPdf = onDownloadCompletionPdf,
                onInvoice = onCreateInvoice
            )

            // Operations tools strip (Inventory/QR, Condition/Photos, Crew Change, Dispatch)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if ("inventory.read" in permissions) {
                    OutlinedButton(onClick = onOpenInventory, modifier = Modifier.testTag("job_inventory_btn")) {
                        Text("Inventory & QR")
                    }
                }
                if ("condition.write" in permissions) {
                    OutlinedButton(onClick = onOpenCondition, modifier = Modifier.testTag("job_condition_btn")) {
                        Text("Condition & Photos (${job.photos.size})")
                    }
                }
                if ("jobs.crew" in permissions && !job.notice && !job.cancelled) {
                    OutlinedButton(onClick = { recordingCrewEvent = true }, modifier = Modifier.testTag("job_crew_event_btn")) {
                        Text("Crew On Site")
                    }
                }
                if (canDispatch) {
                    OutlinedButton(onClick = onOpenAssign) { Text("Assign Crew") }
                    OutlinedButton(onClick = onOpenReschedule) { Text("Reschedule") }
                }
                if (BookingCancel.canOffer(job, hasPermission = "jobs.cancel" in permissions) && !job.notice) {
                    OutlinedButton(
                        onClick = { cancellingBooking = true },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("job_cancel_booking_btn")
                    ) { Text("Cancel booking") }
                }
            }

            Section("Customer") {
                Row("Name", job.customerName)
                Row("Phone", job.customerPhone)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (job.customerPhone != null) {
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + job.customerPhone.filter { it.isDigit() || it == '+' }))) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("job_call")
                        ) { Text("Call") }
                    }
                }
            }
            val canAct = "jobs.act" in permissions
            if ((canAct && job.state == JobState.SCHEDULED && !job.cancelled && !job.notice) || job.milestones != null) {
                JourneySection(job = job, canAct = canAct, busy = busy, onAct = onAct)
            }
            if (job.stops.isNotEmpty()) {
                StopsSection(
                    job = job,
                    canAct = canAct,
                    busy = busy,
                    canEdit = canDispatch && job.state != JobState.COMPLETED && !job.cancelled && !job.notice,
                    onStopEvent = onStopEvent,
                    onEdit = { editingStops = true },
                    context = context
                )
                if (job.travelNotes.isNotBlank()) Section("Travel notes") { Text(job.travelNotes, style = MaterialTheme.typography.bodyMedium) }
            } else {
                Section("Locations") {
                    Row("Pickup", job.pickup)
                    Row("Drop-off", job.destination)
                    if (job.travelNotes.isNotBlank()) Row("Travel notes", job.travelNotes)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        job.pickup?.let { NavButton(context, "Navigate to pickup", it) }
                        job.destination?.let { NavButton(context, "Navigate to drop-off", it) }
                        if (canDispatch && job.state != JobState.COMPLETED && !job.cancelled && !job.notice) {
                            OutlinedButton(onClick = { editingStops = true }, modifier = Modifier.testTag("job_add_stops_btn")) { Text("Add pickup / delivery stops") }
                        }
                    }
                }
            }
            Section("Booking & Dispatch") {
                Row("Truck package", job.truck)
                Row("Booked crew size", job.crewSize?.toString())
                Row("Assigned", job.assignees.takeIf { it.isNotEmpty() }?.joinToString())
                if (job.dispatchNotes.isNotBlank()) Row("Dispatch notes", job.dispatchNotes)
                Row("Notes", job.notes)
                Row("Calendar reference", job.reference)
            }
            Section("Scheduled (from calendar)") {
                Row("Date", JobFormat.localDate(job)?.let { JobFormat.longDay(it) })
                Row("Time", JobFormat.scheduleText(job))
                Row(
                    "Expected duration",
                    if (job.scheduledStart != null && job.scheduledEnd != null) {
                        JobFormat.duration(Duration.between(job.scheduledStart, job.scheduledEnd).toMinutes().toInt())
                    } else null
                )
                Row("Time zone", "Australia/Adelaide")
            }
            Section("Actual (recorded)") {
                Row("Started", job.actualStart?.let { JobFormat.time(it) })
                Row("Finished", job.actualFinish?.let { JobFormat.time(it) })
                Row("Breaks", if (job.actualStart != null) JobFormat.duration(job.breakMinutes) else null)
                Row("Waiting time", if (job.actualStart != null && (job.waitingMinutes > 0 || job.waitActive)) "${JobFormat.duration(job.waitingMinutes)}${if (job.waitActive) " (active)" else ""}" else if (job.actualStart != null) "0h 00m" else null)
                Row("Overtime", if (job.actualStart != null && job.overtimeMinutes > 0) JobFormat.duration(job.overtimeMinutes) else null)
                Row("Elapsed", if (job.actualStart != null) JobFormat.duration(liveElapsedMinutes) else null)
                Row("Working time", if (job.actualStart != null) JobFormat.duration(job.workingMinutes) else null)
                if ("jobs.correct" in permissions && job.actualStart != null) {
                    TextButton(onClick = { correcting = true }, modifier = Modifier.testTag("job_correct")) { Text("Correct a recorded time") }
                }
            }
            if (job.crewEvents.isNotEmpty()) {
                Section("Crew Changes") {
                    job.crewEvents.forEach { ev ->
                        Text(
                            "${ev.memberName} ${ev.eventType} · ${timelineDateTime(ev.at)}${if (ev.customerAcknowledged) " · Customer acknowledged" else ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (job.timeline.isNotEmpty()) {
                Section("Job Timeline") {
                    job.timeline.forEach { entry ->
                        val at = runCatching { Instant.parse(entry.at) }.getOrNull()
                        val dateTime = at?.let(::timelineDateTime)
                        Text(entry.label, style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(dateTime, entry.actor.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                        if (entry.detail.isNotBlank()) Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish this job?") },
            text = { Text("The finish time is recorded now. The scheduled times are not changed.") },
            confirmButton = {
                Button(
                    onClick = { confirmFinish = false; onAct("finish") },
                    modifier = Modifier.testTag("job_finish_confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                ) { Text("Finish Job") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Not yet") } }
        )
    }
    if (correcting) {
        CorrectionDialog(job, onDismiss = { correcting = false }, onSubmit = { f, at, r -> correcting = false; onCorrect(f, at, r) })
    }
    if (recordingCrewEvent) {
        CrewEventDialog(
            defaultCustomerName = job.customerName.orEmpty(),
            onDismiss = { recordingCrewEvent = false },
            onSave = { name, type, ack, ackName, notes, at ->
                recordingCrewEvent = false
                onRecordCrewEvent(name, type, ack, ackName, notes, at)
            }
        )
    }
    if (editingStops) {
        StopsEditorDialog(
            initial = if (job.stops.isNotEmpty()) {
                MovingDay.toDrafts(job.stops)
            } else {
                // Start from the booking's own addresses so dispatch only adds what is missing.
                listOfNotNull(
                    job.pickup?.takeIf { it.isNotBlank() }?.let { StopDraft(null, StopKind.PICKUP, it) },
                    job.destination?.takeIf { it.isNotBlank() }?.let { StopDraft(null, StopKind.DELIVERY, it) }
                )
            },
            onDismiss = { editingStops = false },
            onSave = { editingStops = false; onSaveStops(it) }
        )
    }
    if (cancellingBooking) {
        CancelBookingDialog(
            onDismiss = { cancellingBooking = false },
            onConfirm = { reason -> cancellingBooking = false; onCancelBooking(reason) }
        )
    }
    draft?.let { InvoiceDraftDialog(it, busy, onConfirmDraft, onDismissDraft) }
}

/** A reason is required; the calendar event is not changed (calendar access is read-only). */
@Composable
private fun CancelBookingDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    val trimmed = reason.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this booking?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The booking stops counting for crew, truck and availability checks and cannot be started. " +
                        "The Google Calendar event is not changed.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= BookingCancel.MAX_REASON) reason = it },
                    label = { Text("Reason (required)") },
                    supportingText = { Text("${reason.length}/${BookingCancel.MAX_REASON}") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("job_cancel_reason")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.testTag("job_cancel_confirm")
            ) { Text("Cancel booking") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep booking") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JourneySection(job: Job, canAct: Boolean, busy: Boolean, onAct: (String) -> Unit) {
    val m = job.milestones
    Section("Journey") {
        val status = when (MovingDay.journeyStatus(job)) {
            JourneyStatus.NOT_STARTED -> "Not on the way yet"
            JourneyStatus.TRAVELLING -> "Travelling since ${JobFormat.time(m?.travellingAt)}"
            else -> m?.arrivedAt?.let { "Arrived ${JobFormat.time(it)}" + (m.travelMinutes?.let { t -> " · travel $t min" } ?: "") } ?: "Arrival not recorded"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("journey_status"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (MovingDay.canMarkTravelling(job, canAct)) {
                Button(onClick = { onAct("travelling") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("job_travelling_btn")) { Text("Travelling") }
            }
            if (MovingDay.canMarkArrived(job, canAct)) {
                Button(onClick = { onAct("arrived") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("job_arrived_btn")) { Text("Arrived") }
            }
        }
        Text(
            "Travel time is kept for your records and is not part of charged hours.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopsSection(
    job: Job,
    canAct: Boolean,
    busy: Boolean,
    canEdit: Boolean,
    onStopEvent: (stopId: String, event: String) -> Unit,
    onEdit: () -> Unit,
    context: android.content.Context
) {
    val next = MovingDay.nextStop(job)
    Section("Stops") {
        MovingDay.summary(job)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
        job.stops.sortedBy { it.seq }.forEach { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(if (s.id == next?.id) 2.dp else 1.dp, if (s.id == next?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("stop_card_${s.seq}")
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val status = when (MovingDay.stopStatus(s)) {
                        StopStatus.COMPLETED -> "Done ${JobFormat.time(s.completedAt)}"
                        StopStatus.ARRIVED -> "Arrived ${JobFormat.time(s.arrivedAt)}"
                        StopStatus.PENDING -> if (s.id == next?.id) "Next" else "Pending"
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stop ${s.seq} · ${s.kind.label}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(s.address, style = MaterialTheme.typography.bodyMedium)
                    val contact = listOf(s.contactName, s.contactPhone).filter { it.isNotBlank() }.joinToString(" · ")
                    if (contact.isNotBlank()) Text(contact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavButton(context, "Navigate", s.address)
                        if (MovingDay.canArriveAtStop(job, s, canAct)) {
                            Button(onClick = { onStopEvent(s.id, "arrived") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("stop_arrived_${s.seq}")) { Text("Arrived") }
                        }
                        if (MovingDay.canCompleteStop(job, s, canAct)) {
                            Button(onClick = { onStopEvent(s.id, "completed") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("stop_done_${s.seq}")) { Text("Done") }
                        }
                    }
                }
            }
        }
        if (canEdit) OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("job_edit_stops_btn")) { Text("Edit stops") }
    }
}

/** Dispatch edits the ordered pickup and delivery list. Visited stops keep their place and cannot be removed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopsEditorDialog(initial: List<StopDraft>, onDismiss: () -> Unit, onSave: (List<StopDraft>) -> Unit) {
    var stops by remember { mutableStateOf(initial) }
    val error = MovingDay.validate(stops)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("stops_editor_dialog"),
        title = { Text("Pickup & delivery stops") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stops.forEachIndexed { index, s ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Stop ${index + 1}" + if (s.visited) " · already visited" else "", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StopKind.entries.forEach { kind ->
                                    FilterChip(
                                        selected = s.kind == kind,
                                        enabled = !s.visited,
                                        onClick = { stops = stops.toMutableList().also { it[index] = s.copy(kind = kind) } },
                                        label = { Text(kind.label) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = s.address,
                                onValueChange = { v -> stops = stops.toMutableList().also { it[index] = s.copy(address = v) } },
                                label = { Text("Address") },
                                modifier = Modifier.fillMaxWidth().testTag("stop_address_$index")
                            )
                            OutlinedTextField(
                                value = s.notes,
                                onValueChange = { v -> stops = stops.toMutableList().also { it[index] = s.copy(notes = v) } },
                                label = { Text("Notes (lift, parking, access)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { stops = MovingDay.moveUp(stops, index) }, enabled = index > 0) { Text("Move up") }
                                TextButton(onClick = { stops = MovingDay.moveDown(stops, index) }, enabled = index < stops.size - 1) { Text("Move down") }
                                TextButton(onClick = { stops = MovingDay.remove(stops, index) }, enabled = !s.visited) { Text("Remove") }
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { stops = MovingDay.add(stops, StopKind.PICKUP) }, enabled = stops.size < MovingDay.MAX_STOPS) { Text("Add pickup") }
                    OutlinedButton(onClick = { stops = MovingDay.add(stops, StopKind.DELIVERY) }, enabled = stops.size < MovingDay.MAX_STOPS) { Text("Add delivery") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("stops_editor_error")) }
            }
        },
        confirmButton = { Button(onClick = { onSave(stops) }, enabled = error == null, modifier = Modifier.testTag("stops_editor_save")) { Text("Save stops") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ActionArea(
    job: Job,
    busy: Boolean,
    permissions: Set<String>,
    onAct: (String) -> Unit,
    onConfirmFinish: () -> Unit,
    onOpenSignoff: () -> Unit,
    onPdf: (includePhotos: Boolean) -> Unit,
    onInvoice: () -> Unit
) {
    val canRecordTime = "jobs.act" in permissions
    val big = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            job.cancelled -> Unit
            canRecordTime && job.state == JobState.SCHEDULED -> Button(
                onClick = { onAct("start") },
                enabled = !busy,
                modifier = big.testTag("job_start"),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
            ) {
                Text("Start Job", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            canRecordTime && (job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED) -> {
                val paused = job.state == JobState.PAUSED
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAct(if (paused) "resume" else "pause") },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("job_pause_resume"),
                        border = BorderStroke(1.5.dp, ForestGreen)
                    ) {
                        Text(if (paused) "Resume" else "Pause (break)", color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(
                        onClick = { onAct(if (job.waitActive) "wait_end" else "wait_start") },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("job_wait")
                    ) {
                        Text(if (job.waitActive) "Stop Waiting" else "Wait Time")
                    }
                }
                Button(
                    onClick = onConfirmFinish,
                    enabled = !busy,
                    modifier = big.testTag("job_finish"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = ForestCanopy)
                ) {
                    Text("Finish Job", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            job.state == JobState.COMPLETED -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(
                        when (job.signoff) {
                            SignoffStatus.SIGNED -> "Completed and signed by the customer."
                            SignoffStatus.SKIPPED -> "Completed. Sign-off was skipped, so this job is not customer-signed."
                            SignoffStatus.NONE -> "Completed. Customer sign-off is optional and still open."
                        },
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if ("jobs.signoff" in permissions && job.signoff == SignoffStatus.NONE) {
                    Button(
                        onClick = onOpenSignoff,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("job_signoff"),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                    ) { Text("Customer sign-off") }
                }
                OutlinedButton(onClick = { onPdf(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("job_pdf")) { Text("Completion PDF") }
                if (job.photos.any { it.mimeType == "image/jpeg" || it.mimeType == "image/png" }) {
                    OutlinedButton(onClick = { onPdf(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Completion PDF with photos")
                    }
                }
                if ("invoice.draft" in permissions && "invoices.manage" in permissions) {
                    OutlinedButton(onClick = onInvoice, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("job_create_invoice")) { Text("Create Invoice From Job") }
                }
            }
        }
    }
}

@Composable
private fun NavButton(context: android.content.Context, label: String, address: String) {
    OutlinedButton(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))) },
        modifier = Modifier.heightIn(min = 48.dp)
    ) { Text(label) }
}

@Composable
private fun Banner(text: String, error: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Row(label: String, value: String?) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.38f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "Not provided", Modifier.weight(0.62f), color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CrewEventDialog(
    defaultCustomerName: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, String, String, Instant?) -> Unit
) {
    var memberName by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("joined") }
    var ack by remember { mutableStateOf(false) }
    var ackName by remember { mutableStateOf(defaultCustomerName) }
    var notes by remember { mutableStateOf("") }
    var recordedAt by remember { mutableStateOf("") }
    val at = JobFormat.parseEditable(recordedAt.trim())?.takeIf {
        JobFormat.editable(it) == recordedAt.trim() && !it.isAfter(Instant.now())
    }
    val invalidTime = recordedAt.isNotBlank() && at == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Crew Size Change") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Crew changes are recorded for review. Billing rates are set separately.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(memberName, { memberName = it }, label = { Text("Mover / worker name") }, singleLine = true,
                    modifier = Modifier.testTag("crew_event_name"))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = eventType == "joined", onClick = { eventType = "joined" }, label = { Text("Joined") })
                    FilterChip(selected = eventType == "left", onClick = { eventType = "left" }, label = { Text("Left") })
                }
                OutlinedTextField(
                    recordedAt,
                    { recordedAt = it },
                    label = { Text("Actual time in Adelaide") },
                    placeholder = { Text("YYYY-MM-DD HH:mm") },
                    supportingText = { Text(if (invalidTime) "Enter a valid date and time in the past." else "Leave blank to record the current time.") },
                    singleLine = true,
                    isError = invalidTime,
                    modifier = Modifier.testTag("crew_event_time")
                )
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ack, onCheckedChange = { ack = it })
                    Text("Customer acknowledged crew change on site", style = MaterialTheme.typography.bodySmall)
                }
                if (ack) OutlinedTextField(ackName, { ackName = it }, label = { Text("Customer name") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(memberName, eventType, ack, ackName, notes, at) },
                enabled = memberName.isNotBlank() && !invalidTime && (!ack || ackName.isNotBlank())
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun timelineDateTime(at: Instant?): String = at?.let {
    "${JobFormat.longDay(it.atZone(JobFormat.zone).toLocalDate())}, ${JobFormat.time(it)}"
} ?: "Time not recorded"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorrectionDialog(job: Job, onDismiss: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var field by remember { mutableStateOf(if (job.actualFinish != null) "finish" else "start") }
    var text by remember { mutableStateOf(JobFormat.editable(if (field == "finish") job.actualFinish else job.actualStart)) }
    var reason by remember { mutableStateOf("") }
    val parsed = JobFormat.parseEditable(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct recorded time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The original value stays in the audit history. Scheduled times are never changed.", style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("start" to "Start", "finish" to "Finish").forEach { (f, l) ->
                        if (f == "start" || job.actualFinish != null) {
                            FilterChip(selected = field == f, onClick = { field = f; text = JobFormat.editable(if (f == "finish") job.actualFinish else job.actualStart) }, label = { Text(l) })
                        }
                    }
                }
                OutlinedTextField(text, { text = it }, label = { Text("Adelaide time (yyyy-MM-dd HH:mm)") }, isError = parsed == null, singleLine = true)
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason (required)") }, modifier = Modifier.testTag("correct_reason"))
            }
        },
        confirmButton = { Button(onClick = { onSubmit(field, parsed!!.toString(), reason) }, enabled = parsed != null && reason.isNotBlank()) { Text("Save correction") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InvoiceDraftDialog(draft: DraftReview, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review draft invoice") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Customer: ${draft.customerName ?: "Not provided. Add one before issuing."}", fontWeight = FontWeight.SemiBold)
                Text("Working time recorded: ${JobFormat.duration(draft.workingMinutes)}")
                draft.summary.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodyMedium) }
                draft.warnings.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("This saves a draft only. Nothing is issued or sent, and customer sign-off does not approve any extra charges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.testTag("draft_confirm")) { Text("Save as draft") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
