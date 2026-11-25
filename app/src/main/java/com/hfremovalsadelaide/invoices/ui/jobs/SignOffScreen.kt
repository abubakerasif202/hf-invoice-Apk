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
import androidx.compose.material3.Surface
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
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite

/**
 * Shown after Finish Job.
 * Separately records work completion acknowledgment vs optional additional charges acknowledgment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignOffScreen(
    job: Job,
    busy: Boolean,
    onSubmit: (name: String, pngBase64: String) -> Unit,
    onSkip: (reason: String) -> Unit,
    onDismiss: () -> Unit,
    onSubmitDetailed: ((name: String, pngBase64: String, chargesAcknowledged: Boolean, damageNotes: String, outstandingItems: String) -> Unit)? = null,
    onSkipDetailed: ((reason: String, damageNotes: String, outstandingItems: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    var name by remember { mutableStateOf(job.customerName.orEmpty()) }
    var agreed by remember { mutableStateOf(false) }
    var chargesAcknowledged by remember { mutableStateOf(false) }
    var showExceptions by remember { mutableStateOf(false) }
    var damageNotes by remember { mutableStateOf("") }
    var outstandingItems by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var current by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var skipping by remember { mutableStateOf(false) }
    var skipReason by remember { mutableStateOf("Customer Unavailable") }
    var otherReason by remember { mutableStateOf("") }
    val hasInk = SignatureRenderer.hasInk(strokes)

    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(ForestGreen).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.hfremovalsadelaide.invoices.R.drawable.hf_logo),
                contentDescription = "HF Removals Adelaide logo",
                modifier = Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).testTag("signoff_logo")
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text("HF REMOVALS ADELAIDE", color = GoldAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Job Completion & Customer Sign-Off", color = PureWhite, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "${job.customerName ?: "Customer"} · ${job.reference ?: job.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${job.pickup ?: "-"} -> ${job.destination ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Time: ${job.actualStart?.let { JobFormat.time(it) } ?: "-"} to ${job.actualFinish?.let { JobFormat.time(it) } ?: "-"} · Working ${JobFormat.duration(job.workingMinutes)} (Breaks ${JobFormat.duration(job.breakMinutes)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!skipping) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Customer name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("signoff_name")
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Customer signature", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { strokes = emptyList(); current = emptyList() }, modifier = Modifier.testTag("signature_clear")) { Text("Clear") }
                }
                Canvas(
                    Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it }, modifier = Modifier.testTag("signoff_ack"))
                    Text(
                        "Work completion summary is accurate. Signing does not waive statutory rights under Australian law and does not approve extra charges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = chargesAcknowledged, onCheckedChange = { chargesAcknowledged = it }, modifier = Modifier.testTag("signoff_charges_ack"))
                    Text(
                        "Separate optional: confirm extra waiting/crew/packing charges discussed on site.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { showExceptions = !showExceptions }) {
                    Text(if (showExceptions) "Hide damage / outstanding notes" else "Add damage or outstanding item notes")
                }
                if (showExceptions) {
                    OutlinedTextField(damageNotes, { damageNotes = it }, label = { Text("Damage noted (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(outstandingItems, { outstandingItems = it }, label = { Text("Outstanding items or tasks (optional)") }, modifier = Modifier.fillMaxWidth())
                }

                Button(
                    onClick = {
                        val png = SignatureRenderer.toPngBase64(strokes, box.width.toFloat(), box.height.toFloat())
                        if (onSubmitDetailed != null) {
                            onSubmitDetailed(name, png, chargesAcknowledged, damageNotes, outstandingItems)
                        } else {
                            onSubmit(name, png)
                        }
                    },
                    enabled = !busy && agreed && hasInk && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("signoff_confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                ) { Text("Confirm and Save") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { skipping = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("signoff_skip")) { Text("Skip Sign-Off") }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("signoff_later")) { Text("Decide later") }
                }
            } else {
                Text("Skip sign-off", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("The job stays completed, but is recorded as not customer-signed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Customer Unavailable", "Customer Declined", "Other").forEach { r ->
                        FilterChip(selected = skipReason == r, onClick = { skipReason = r }, label = { Text(r) })
                    }
                }
                if (skipReason == "Other") OutlinedTextField(otherReason, { otherReason = it }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(damageNotes, { damageNotes = it }, label = { Text("Damage noted (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        val r = if (skipReason == "Other") otherReason else skipReason
                        if (onSkipDetailed != null) {
                            onSkipDetailed(r, damageNotes, outstandingItems)
                        } else {
                            onSkip(r)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("signoff_skip_confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestCanopy, contentColor = PureWhite)
                ) { Text("Skip and finish") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { skipping = false }) { Text("Back to signature") }
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("signoff_later")) { Text("Decide later") }
                }
            }
        }
    }
}
