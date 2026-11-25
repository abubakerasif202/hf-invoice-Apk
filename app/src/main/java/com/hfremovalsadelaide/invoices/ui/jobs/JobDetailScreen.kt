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
import com.hfremovalsadelaide.invoices.jobs.DraftReview
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.jobs.JobState
import com.hfremovalsadelaide.invoices.jobs.SignoffStatus
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
    onDownloadCompletionPdf: () -> Unit,
    onCreateInvoice: () -> Unit,
    onConfirmDraft: () -> Unit,
    onDismissDraft: () -> Unit,
    permissions: Set<String>,
    canDispatch: Boolean = false,
    onOpenAssign: () -> Unit = {},
    onOpenReschedule: () -> Unit = {},
    onOpenInventory: () -> Unit = {},
    onOpenCondition: () -> Unit = {},
    onRecordCrewEvent: (memberName: String, eventType: String, ack: Boolean, ackName: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
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
            if (job.notice) Banner("This calendar entry is a notice or note, not a job, so it cannot be started.", false)
            if (job.cancelled) Banner("Cancelled in the calendar. Recorded times are kept.", true)
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
            Section("Locations") {
                Row("Pickup", job.pickup)
                Row("Drop-off", job.destination)
                if (job.travelNotes.isNotBlank()) Row("Travel notes", job.travelNotes)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    job.pickup?.let { NavButton(context, "Navigate to pickup", it) }
                    job.destination?.let { NavButton(context, "Navigate to drop-off", it) }
                }
            }
            Section("Booking & Dispatch") {
                Row("Truck package", job.truck)
                Row("Crew size", job.crewSize?.toString())
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
                            "• ${ev.memberName} ${ev.eventType} at ${JobFormat.time(ev.at)}${if (ev.customerAcknowledged) " (Customer ack)" else ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (job.timeline.isNotEmpty()) {
                Section("Job Timeline") {
                    job.timeline.forEach { entry ->
                        Text("• ${entry.label} (${entry.actor}) — ${entry.detail.ifBlank { entry.at.take(19) }}", style = MaterialTheme.typography.bodySmall)
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
            onSave = { name, type, ack, ackName, notes ->
                recordingCrewEvent = false
                onRecordCrewEvent(name, type, ack, ackName, notes)
            }
        )
    }
    draft?.let { InvoiceDraftDialog(it, busy, onConfirmDraft, onDismissDraft) }
}

@Composable
private fun ActionArea(
    job: Job,
    busy: Boolean,
    permissions: Set<String>,
    onAct: (String) -> Unit,
    onConfirmFinish: () -> Unit,
    onOpenSignoff: () -> Unit,
    onPdf: () -> Unit,
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
                OutlinedButton(onClick = onPdf, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("job_pdf")) { Text("Completion PDF") }
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
    onSave: (String, String, Boolean, String, String) -> Unit
) {
    var memberName by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("joined") }
    var ack by remember { mutableStateOf(false) }
    var ackName by remember { mutableStateOf(defaultCustomerName) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Crew Size Change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Logs extra movers joining or leaving on site without altering agreed rates silently.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(memberName, { memberName = it }, label = { Text("Mover / worker name") }, singleLine = true)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = eventType == "joined", onClick = { eventType = "joined" }, label = { Text("Joined") })
                    FilterChip(selected = eventType == "left", onClick = { eventType = "left" }, label = { Text("Left") })
                }
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ack, onCheckedChange = { ack = it })
                    Text("Customer acknowledged crew change on site", style = MaterialTheme.typography.bodySmall)
                }
                if (ack) OutlinedTextField(ackName, { ackName = it }, label = { Text("Customer name") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(memberName, eventType, ack, ackName, notes) }, enabled = memberName.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

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
