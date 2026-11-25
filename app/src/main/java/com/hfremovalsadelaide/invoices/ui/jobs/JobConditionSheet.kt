package com.hfremovalsadelaide.invoices.ui.jobs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hfremovalsadelaide.invoices.jobs.ConditionReportItem
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JobConditionSheet(
    job: Job,
    busy: Boolean,
    onSaveConditionReport: (ConditionReportItem) -> Unit,
    onUploadPhoto: (phase: String, caption: String, imageBase64: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var phase by remember { mutableStateOf("before") }
    val existing = job.conditionReports.firstOrNull { it.phase == phase }

    var accessConditions by remember(phase, existing) { mutableStateOf(existing?.accessConditions.orEmpty()) }
    var existingDamage by remember(phase, existing) { mutableStateOf(existing?.existingDamage.orEmpty()) }
    var delaysOrIssues by remember(phase, existing) { mutableStateOf(existing?.delaysOrIssues.orEmpty()) }
    var additionalServices by remember(phase, existing) { mutableStateOf(existing?.additionalServices.orEmpty()) }
    var checklist by remember(phase, existing) {
        mutableStateOf(
            existing?.checklist ?: mapOf(
                "Access & parking verified" to false,
                "Fragile & high-value items inspected" to false,
                "Pathways & floors protected" to false,
                "Post-move walkthrough complete" to false
            )
        )
    }
    var acknowledged by remember(phase, existing) { mutableStateOf(existing?.customerAcknowledged ?: false) }
    var ackName by remember(phase, existing) { mutableStateOf(existing?.acknowledgmentName ?: job.customerName.orEmpty()) }
    var notes by remember(phase, existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var photoCaption by remember { mutableStateOf("") }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = tempCameraUri
        tempCameraUri = null
        if (uri != null) {
            try {
                if (ok) encodeUriToBase64(context, uri)?.let { b64 ->
                    onUploadPhoto(phase, photoCaption.ifBlank { "$phase photo" }, b64)
                    photoCaption = ""
                }
            } finally {
                deleteCameraCapture(context, uri)
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            encodeUriToBase64(context, uri)?.let { b64 ->
                onUploadPhoto(phase, photoCaption.ifBlank { "$phase photo" }, b64)
                photoCaption = ""
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Condition Reports & Private Photos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(
                "Reports and photos stay private in encrypted storage and server-side records unless explicitly shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("before" to "Before Move", "during" to "During Move", "after" to "After Move").forEach { (key, label) ->
                    FilterChip(selected = phase == key, onClick = { phase = key }, label = { Text(label) })
                }
            }

            if (phase == "before") {
                OutlinedTextField(accessConditions, { accessConditions = it }, label = { Text("Access conditions (stairs, lift, truck distance)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(existingDamage, { existingDamage = it }, label = { Text("Existing damage before move") }, modifier = Modifier.fillMaxWidth())
            }
            if (phase == "during") {
                OutlinedTextField(delaysOrIssues, { delaysOrIssues = it }, label = { Text("Access delays / unexpected issues") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(additionalServices, { additionalServices = it }, label = { Text("Additional services performed") }, modifier = Modifier.fillMaxWidth())
            }
            if (phase == "after") {
                OutlinedTextField(existingDamage, { existingDamage = it }, label = { Text("Post-move damage or outstanding items") }, modifier = Modifier.fillMaxWidth())
                Text("Completion Checklist", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                checklist.forEach { (item, checked) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { v -> checklist = checklist + (item to v) })
                        Text(item, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Condition notes") }, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                Text("Customer inspected & acknowledged ($phase stage)", style = MaterialTheme.typography.bodySmall)
            }
            if (acknowledged) {
                OutlinedTextField(ackName, { ackName = it }, label = { Text("Customer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = {
                    onSaveConditionReport(
                        ConditionReportItem(
                            id = existing?.id.orEmpty(),
                            phase = phase,
                            accessConditions = accessConditions,
                            existingDamage = existingDamage,
                            delaysOrIssues = delaysOrIssues,
                            additionalServices = additionalServices,
                            checklist = checklist,
                            customerAcknowledged = acknowledged,
                            acknowledgmentName = ackName,
                            notes = notes
                        )
                    )
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save ${phase.replaceFirstChar { it.uppercase() }} Condition Report")
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attach Private Job Photo (${job.photos.size} saved)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(photoCaption, { photoCaption = it }, label = { Text("Caption (e.g. Piano corner prior mark)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                            val photoFile = File(cameraDir, "hf_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                            tempCameraUri = uri
                            takePicture.launch(uri)
                        }) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Camera")
                        }
                        OutlinedButton(onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Gallery")
                        }
                    }
                    job.photos.forEach { p ->
                        Text("• [${p.phase}] ${p.caption.ifBlank { "Photo" }} (${p.createdAt.take(16)})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun deleteCameraCapture(context: Context, uri: Uri) {
    context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    context.contentResolver.delete(uri, null, null)
}

internal fun clearCameraCaptures(context: Context) {
    val dir = File(context.cacheDir, "camera")
    dir.listFiles().orEmpty().forEach { file ->
        deleteCameraCapture(context, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
}

private fun encodeUriToBase64(context: Context, uri: Uri): String? = runCatching {
    val rawBmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
    val maxDim = 1280
    val scale = maxOf(rawBmp.width, rawBmp.height).let { if (it > maxDim) maxDim.toFloat() / it.toFloat() else 1f }
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(rawBmp, (rawBmp.width * scale).toInt(), (rawBmp.height * scale).toInt(), true)
    } else rawBmp
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}.getOrNull()
