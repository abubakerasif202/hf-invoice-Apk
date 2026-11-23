package com.hfremovalsadelaide.invoices.ui.jobs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.jobs.SignatureRenderer
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.MutedSage
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.ui.theme.SageOutline

/** Shown after Finish Job. Entirely optional: the driver can skip, and a skipped job stays "completed, not signed". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignOffScreen(
    job: Job,
    busy: Boolean,
    onSubmit: (name: String, pngBase64: String) -> Unit,
    onSkip: (reason: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    var name by remember { mutableStateOf(job.customerName.orEmpty()) }
    var agreed by remember { mutableStateOf(false) }
    var strokes by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var current by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var skipping by remember { mutableStateOf(false) }
    var skipReason by remember { mutableStateOf("Customer Unavailable") }
    var otherReason by remember { mutableStateOf("") }
    val hasInk = SignatureRenderer.hasInk(strokes)

    Column(modifier.background(PureWhite)) {
        Row(Modifier.fillMaxWidth().background(ForestGreen).statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.hfremovalsadelaide.invoices.R.drawable.hf_logo),
                contentDescription = "HF Removals Adelaide logo",
                modifier = Modifier.height(64.dp).clip(RoundedCornerShape(8.dp)).testTag("signoff_logo")
            )
            Column(Modifier.padding(start = 14.dp)) {
                Text("HF REMOVALS ADELAIDE", color = GoldAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Job Completion & Customer Sign-Off", color = PureWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Line("Customer", job.customerName)
            Line("Job reference", job.reference ?: job.title)
            Line("Service date", job.actualStart?.let { java.time.LocalDate.ofInstant(it, JobFormat.zone) }?.let { JobFormat.longDay(it) })
            Line("Pickup", job.pickup)
            Line("Delivery", job.destination)
            Line("Actual start", job.actualStart?.let { JobFormat.time(it) })
            Line("Actual finish", job.actualFinish?.let { JobFormat.time(it) })
            Line("Recorded breaks", if (job.breakMinutes > 0) JobFormat.duration(job.breakMinutes) else "None recorded")
            Line("Total time", JobFormat.duration(job.elapsedMinutes) + " (working " + JobFormat.duration(job.workingMinutes) + ")")
            Line("Notes", job.notes)

            if (!skipping) {
                OutlinedTextField(name, { name = it }, label = { Text("Customer name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("signoff_name"))
                Text("Customer signature", style = MaterialTheme.typography.titleSmall, color = ForestGreen, fontWeight = FontWeight.Bold)
                Canvas(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .border(BorderStroke(1.5.dp, SageOutline), RoundedCornerShape(12.dp))
                        .onSizeChanged { box = it }
                        .semantics { contentDescription = "Signature box. Draw with your finger." }
                        .testTag("signature_canvas")
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> current = listOf(o.x to o.y) },
                                onDrag = { change, _ -> change.consume(); current = current + (change.position.x to change.position.y) },
                                onDragEnd = { if (current.size >= 2) strokes = strokes + listOf(current); current = emptyList() },
                                onDragCancel = { current = emptyList() }
                            )
                        }
                ) {
                    (strokes + listOf(current)).filter { it.size >= 2 }.forEach { s ->
                        val path = Path().apply { moveTo(s.first().first, s.first().second); s.drop(1).forEach { lineTo(it.first, it.second) } }
                        drawPath(path, Color.Black, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                TextButton(onClick = { strokes = emptyList(); current = emptyList() }, modifier = Modifier.testTag("signature_clear")) { Text("Clear") }

                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it }, modifier = Modifier.testTag("signoff_ack"))
                    Text(
                        "I confirm the completion summary shown above is a fair record of the work done today. Signing does not waive any of my rights under Australian law and does not approve any additional charges.",
                        style = MaterialTheme.typography.bodySmall, color = ForestCanopy, modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Button(
                    onClick = { onSubmit(name, SignatureRenderer.toPngBase64(strokes, box.width.toFloat(), box.height.toFloat())) },
                    enabled = !busy && agreed && hasInk && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("signoff_confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                ) { Text("Confirm and Save") }
                OutlinedButton(onClick = { skipping = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("signoff_skip")) { Text("Skip Sign-Off") }
            } else {
                Text("Skip sign-off", style = MaterialTheme.typography.titleSmall, color = ForestGreen, fontWeight = FontWeight.Bold)
                Text("The job stays completed, but is recorded as not customer-signed.", style = MaterialTheme.typography.bodySmall, color = MutedSage)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Customer Unavailable", "Customer Declined", "Other").forEach { r ->
                        FilterChip(selected = skipReason == r, onClick = { skipReason = r }, label = { Text(r) })
                    }
                }
                if (skipReason == "Other") OutlinedTextField(otherReason, { otherReason = it }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onSkip(if (skipReason == "Other") otherReason else skipReason) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("signoff_skip_confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestCanopy, contentColor = PureWhite)
                ) { Text("Skip and finish") }
                TextButton(onClick = { skipping = false }) { Text("Back to signature") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("signoff_later")) { Text("Decide later") }
        }
    }
}

@Composable
private fun Line(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.38f), color = MutedSage, style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "-", Modifier.weight(0.62f), color = ForestCanopy, style = MaterialTheme.typography.bodyMedium)
    }
}
