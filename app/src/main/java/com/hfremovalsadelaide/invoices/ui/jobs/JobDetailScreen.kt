package com.hfremovalsadelaide.invoices.ui.jobs

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
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
import com.hfremovalsadelaide.invoices.ui.theme.ForestSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.MutedSage
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.ui.theme.SageOutline

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
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var confirmFinish by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf(false) }

    Column(modifier.background(PureWhite)) {
        Row(Modifier.fillMaxWidth().background(ForestGreen).statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to jobs", tint = PureWhite) }
            Text(job.title.ifBlank { "Booking" }, color = PureWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (job.cancelled) Banner("Cancelled in the calendar. Recorded times are kept.", true)
            if (job.bookingStatus != com.hfremovalsadelaide.invoices.jobs.BookingStatus.CONFIRMED) Banner("Booking details are not marked confirmed. Check with the customer before relying on them.", false)

            ActionArea(job, busy, onAct, onConfirmFinish = { confirmFinish = true }, onOpenSignoff, onDownloadCompletionPdf, onCreateInvoice)

            Section("Customer") {
                Row("Name", job.customerName)
                Row("Phone", job.customerPhone)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (job.customerPhone != null) OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + job.customerPhone.filter { it.isDigit() || it == '+' }))) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("job_call")
                    ) { Text("Call") }
                }
            }
            Section("Locations") {
                Row("Pickup", job.pickup)
                Row("Drop-off", job.destination)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    job.pickup?.let { NavButton(context, "Navigate to pickup", it) }
                    job.destination?.let { NavButton(context, "Navigate to drop-off", it) }
                }
            }
            Section("Booking") {
                Row("Truck package", job.truck)
                Row("Crew size", job.crewSize?.toString())
                Row("Assigned", job.assignees.takeIf { it.isNotEmpty() }?.joinToString())
                Row("Notes", job.notes)
                Row("Calendar reference", job.reference)
            }
            Section("Scheduled (from calendar)") {
                Row("Date", JobFormat.localDate(job)?.let { JobFormat.longDay(it) })
                Row("Time", JobFormat.scheduleText(job))
                Row("Expected duration", if (job.scheduledStart != null && job.scheduledEnd != null) JobFormat.duration(java.time.Duration.between(job.scheduledStart, job.scheduledEnd).toMinutes().toInt()) else null)
                Row("Time zone", "Australia/Adelaide")
            }
            Section("Actual (recorded)") {
                Row("Started", job.actualStart?.let { JobFormat.time(it) })
                Row("Finished", job.actualFinish?.let { JobFormat.time(it) })
                Row("Breaks", if (job.actualStart != null) JobFormat.duration(job.breakMinutes) else null)
                Row("Elapsed", if (job.actualStart != null) JobFormat.duration(job.elapsedMinutes) else null)
                Row("Working time", if (job.actualStart != null) JobFormat.duration(job.workingMinutes) else null)
                if (job.actualStart != null) TextButton(onClick = { correcting = true }, modifier = Modifier.testTag("job_correct")) { Text("Correct a recorded time") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("Finish this job?") },
        text = { Text("The finish time is recorded now. The scheduled times are not changed.") },
        confirmButton = { Button(onClick = { confirmFinish = false; onAct("finish") }, colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)) { Text("Finish Job") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Not yet") } }
    )
    if (correcting) CorrectionDialog(job, onDismiss = { correcting = false }, onSubmit = { f, at, r -> correcting = false; onCorrect(f, at, r) })
    draft?.let { InvoiceDraftDialog(it, busy, onConfirmDraft, onDismissDraft) }
}

@Composable
private fun ActionArea(job: Job, busy: Boolean, onAct: (String) -> Unit, onConfirmFinish: () -> Unit, onOpenSignoff: () -> Unit, onPdf: () -> Unit, onInvoice: () -> Unit) {
    val big = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            job.cancelled -> Unit
            job.state == JobState.SCHEDULED -> Button(onClick = { onAct("start") }, enabled = !busy, modifier = big.testTag("job_start"), colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)) {
                Text("Start Job", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED -> {
                val paused = job.state == JobState.PAUSED
                OutlinedButton(onClick = { onAct(if (paused) "resume" else "pause") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("job_pause_resume"), border = BorderStroke(1.5.dp, ForestGreen)) {
                    Text(if (paused) "Resume" else "Pause (break)", color = ForestGreen)
                }
                Button(onClick = onConfirmFinish, enabled = !busy, modifier = big.testTag("job_finish"), colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = ForestCanopy)) {
                    Text("Finish Job", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            job.state == JobState.COMPLETED -> {
                Card(colors = CardDefaults.cardColors(containerColor = ForestSoftTint)) {
                    Text(
                        when (job.signoff) {
                            SignoffStatus.SIGNED -> "Completed and signed by the customer."
                            SignoffStatus.SKIPPED -> "Completed. Sign-off was skipped, so this job is not customer-signed."
                            SignoffStatus.NONE -> "Completed. Customer sign-off is optional and still open."
                        },
                        Modifier.padding(12.dp), color = ForestCanopy
                    )
                }
                if (job.signoff == SignoffStatus.NONE) Button(onClick = onOpenSignoff, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("job_signoff"), colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)) { Text("Customer sign-off") }
                OutlinedButton(onClick = onPdf, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("job_pdf")) { Text("Completion PDF") }
                OutlinedButton(onClick = onInvoice, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("job_create_invoice")) { Text("Create Invoice From Job") }
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
    Card(colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else GoldAccent.copy(alpha = 0.22f))) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = ForestCanopy)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = ForestGreen, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Row(label: String, value: String?) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.38f), color = MutedSage, style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "Not provided", Modifier.weight(0.62f), color = if (value == null) MutedSage else ForestCanopy, style = MaterialTheme.typography.bodyMedium)
    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("start" to "Start", "finish" to "Finish").forEach { (f, l) ->
                        if (f == "start" || job.actualFinish != null) FilterChip(selected = field == f, onClick = { field = f; text = JobFormat.editable(if (f == "finish") job.actualFinish else job.actualStart) }, label = { Text(l) })
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
                Text("This saves a draft only. Nothing is issued or sent, and customer sign-off does not approve any extra charges.", style = MaterialTheme.typography.bodySmall, color = MutedSage)
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.testTag("draft_confirm")) { Text("Save as draft") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
