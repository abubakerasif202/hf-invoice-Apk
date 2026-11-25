package com.hfremovalsadelaide.invoices.ui.jobs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.jobs.InventoryItem
import com.hfremovalsadelaide.invoices.jobs.InventoryRoom
import com.hfremovalsadelaide.invoices.jobs.InventoryStatus
import com.hfremovalsadelaide.invoices.jobs.JobInventoryState
import com.hfremovalsadelaide.invoices.jobs.PackingRule
import com.hfremovalsadelaide.invoices.jobs.QrCodeTool
import com.hfremovalsadelaide.invoices.ui.components.StatusBadge
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JobInventorySheet(
    jobId: String,
    jobTitle: String,
    inventory: JobInventoryState?,
    loading: Boolean,
    busy: Boolean,
    onAddRoom: (String) -> Unit,
    onSaveItem: (InventoryItem) -> Unit,
    onScanBarcode: (barcode: String, status: InventoryStatus, note: String) -> Unit,
    onDownloadInventoryPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    var showAddRoom by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }
    var scannedCodeFromImage by remember { mutableStateOf("") }
    var qrPreviewItem by remember { mutableStateOf<InventoryItem?>(null) }

    val qrPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val bmp = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            val decoded = bmp?.let { QrCodeTool.decodeFromBitmap(it) }
            if (!decoded.isNullOrBlank()) {
                scannedCodeFromImage = decoded
                showScanDialog = true
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Moving Inventory & QR", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(jobTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onDownloadInventoryPdf) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("PDF")
                }
            }

            if (loading && inventory == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                val rooms = inventory?.rooms.orEmpty()
                val items = inventory?.items.orEmpty()
                val loadedCount = items.count { it.status == InventoryStatus.LOADED || it.status == InventoryStatus.DELIVERED || it.status == InventoryStatus.UNPACKED }
                val deliveredCount = items.count { it.status == InventoryStatus.DELIVERED || it.status == InventoryStatus.UNPACKED }
                val issueCount = items.count { it.status == InventoryStatus.EXCEPTION || it.status == InventoryStatus.MISSING }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        ReconciliationStat("Items", items.sumOf { it.quantity }.toString())
                        ReconciliationStat("Loaded", "$loadedCount/${items.size}")
                        ReconciliationStat("Delivered", "$deliveredCount/${items.size}")
                        ReconciliationStat("Issues", issueCount.toString())
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAddRoom = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Add Room")
                    }
                    Button(
                        onClick = { showAddItem = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Add Item")
                    }
                    OutlinedButton(onClick = { showScanDialog = true }) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Scan / Update")
                    }
                    OutlinedButton(onClick = {
                        qrPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Scan Photo")
                    }
                }

                if (rooms.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(selected = selectedRoomId == null, onClick = { selectedRoomId = null }, label = { Text("All (${items.size})") })
                        rooms.forEach { r ->
                            FilterChip(
                                selected = selectedRoomId == r.id,
                                onClick = { selectedRoomId = r.id },
                                label = { Text(r.name) }
                            )
                        }
                    }
                }

                val filtered = items.filter { selectedRoomId == null || it.roomId == selectedRoomId }
                if (filtered.isEmpty()) {
                    Text("No inventory items recorded in this room yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filtered.forEach { item ->
                        InventoryItemCard(
                            item = item,
                            rooms = rooms,
                            busy = busy,
                            onStatusChange = { status -> onSaveItem(item.copy(status = status)) },
                            onShowQr = { qrPreviewItem = item }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showAddRoom) {
        var roomName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddRoom = false },
            title = { Text("Add Room or Commercial Zone") },
            text = { OutlinedTextField(roomName, { roomName = it }, label = { Text("e.g. Master Bedroom, Server Room") }, singleLine = true) },
            confirmButton = {
                Button(onClick = { showAddRoom = false; onAddRoom(roomName) }, enabled = roomName.isNotBlank()) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddRoom = false }) { Text("Cancel") } }
        )
    }

    if (showAddItem) {
        AddInventoryItemDialog(
            jobId = jobId,
            rooms = inventory?.rooms.orEmpty(),
            rules = inventory?.packingRules.orEmpty(),
            defaultRoomId = selectedRoomId,
            onDismiss = { showAddItem = false },
            onSave = {
                showAddItem = false
                onSaveItem(it)
            }
        )
    }

    if (showScanDialog) {
        ScanBarcodeDialog(
            initialCode = scannedCodeFromImage,
            onDismiss = { showScanDialog = false; scannedCodeFromImage = "" },
            onApply = { code, status, note ->
                showScanDialog = false
                scannedCodeFromImage = ""
                onScanBarcode(code, status, note)
            }
        )
    }

    qrPreviewItem?.let { item ->
        val bmp: Bitmap? = remember(item.barcode) { QrCodeTool.generateQrBitmap(item.barcode.ifBlank { item.id }) }
        AlertDialog(
            onDismissRequest = { qrPreviewItem = null },
            title = { Text("Carton / Asset Label") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    bmp?.let {
                        Image(bitmap = it.asImageBitmap(), contentDescription = "QR Label", modifier = Modifier.size(200.dp))
                    }
                    Text("Code: ${item.barcode.ifBlank { item.id }}", style = MaterialTheme.typography.labelLarge)
                }
            },
            confirmButton = { TextButton(onClick = { qrPreviewItem = null }) { Text("Done") } }
        )
    }
}

@Composable
private fun ReconciliationStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    rooms: List<InventoryRoom>,
    busy: Boolean,
    onStatusChange: (InventoryStatus) -> Unit,
    onShowQr: () -> Unit
) {
    val roomName = rooms.firstOrNull { it.id == item.roomId }?.name
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${item.quantity}x ${item.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            roomName,
                            item.category,
                            item.dimensions.takeIf { it.isNotBlank() },
                            if (item.isFragile) "FRAGILE" else null,
                            if (item.isHeavy) "HEAVY" else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(
                    label = item.status.label,
                    containerColor = if (item.status == InventoryStatus.EXCEPTION || item.status == InventoryStatus.MISSING) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (item.status == InventoryStatus.EXCEPTION || item.status == InventoryStatus.MISSING) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
            if (item.packingMethod.isNotBlank() || item.packingChargeRate != null) {
                Text(
                    "Packing: ${item.packingMethod.ifBlank { "Standard" }}${item.packingChargeRate?.let { " · $$it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (item.conditionBefore.isNotBlank() || item.conditionAfter.isNotBlank() || item.exceptionNote.isNotBlank()) {
                Text(
                    listOfNotNull(
                        item.conditionBefore.takeIf { it.isNotBlank() }?.let { "Before: $it" },
                        item.conditionAfter.takeIf { it.isNotBlank() }?.let { "After: $it" },
                        item.exceptionNote.takeIf { it.isNotBlank() }?.let { "Issue: $it" }
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onShowQr) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(item.barcode.ifBlank { "QR" })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(InventoryStatus.PACKED, InventoryStatus.LOADED, InventoryStatus.DELIVERED, InventoryStatus.UNPACKED, InventoryStatus.EXCEPTION)
                        .forEach { nextStatus ->
                            FilterChip(
                                selected = item.status == nextStatus,
                                onClick = { if (!busy) onStatusChange(nextStatus) },
                                label = { Text(nextStatus.label) }
                            )
                        }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddInventoryItemDialog(
    jobId: String,
    rooms: List<InventoryRoom>,
    rules: List<PackingRule>,
    defaultRoomId: String?,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit
) {
    var roomId by remember { mutableStateOf(defaultRoomId ?: rooms.firstOrNull()?.id) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("furniture") }
    var qty by remember { mutableStateOf("1") }
    var dimensions by remember { mutableStateOf("") }
    var isFragile by remember { mutableStateOf(false) }
    var isHeavy by remember { mutableStateOf(false) }
    var packingMethod by remember { mutableStateOf("") }
    var packingRate by remember { mutableStateOf("") }
    var conditionBefore by remember { mutableStateOf("") }
    val generatedCode = remember { "HFR-${jobId.take(4).uppercase()}-${(100..999).random()}" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Inventory / Carton") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rooms.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rooms.forEach { r ->
                            FilterChip(selected = roomId == r.id, onClick = { roomId = r.id }, label = { Text(r.name) })
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Item name (e.g. Oak Dining Table, Monitor x2)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("furniture", "carton", "it_equipment", "office_desk", "appliance").forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(qty, { qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(dimensions, { dimensions = it }, label = { Text("Dimensions / Weight") }, modifier = Modifier.weight(2f), singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFragile, onCheckedChange = { isFragile = it })
                    Text("Fragile", modifier = Modifier.padding(end = 12.dp))
                    Checkbox(checked = isHeavy, onCheckedChange = { isHeavy = it })
                    Text("Heavy / 2+ Crew")
                }
                if (rules.isNotEmpty()) {
                    Text("Optional admin packing rules:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rules.forEach { rule ->
                            FilterChip(
                                selected = packingMethod == rule.name,
                                onClick = {
                                    packingMethod = rule.name
                                    packingRate = rule.unitPrice
                                },
                                label = { Text("${rule.name} ($${rule.unitPrice})") }
                            )
                        }
                    }
                }
                OutlinedTextField(packingMethod, { packingMethod = it }, label = { Text("Packing method (blanket, carton, crate)") }, singleLine = true)
                OutlinedTextField(conditionBefore, { conditionBefore = it }, label = { Text("Pre-move condition note") })
                Text("Assigned QR / Barcode: $generatedCode", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        InventoryItem(
                            id = "",
                            roomId = roomId,
                            name = name.trim(),
                            category = category,
                            quantity = qty.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            dimensions = dimensions.trim(),
                            isFragile = isFragile,
                            isHeavy = isHeavy,
                            packingMethod = packingMethod.trim(),
                            packingChargeRate = packingRate.trim().ifBlank { null },
                            barcode = generatedCode,
                            status = InventoryStatus.PENDING,
                            conditionBefore = conditionBefore.trim(),
                            conditionAfter = "",
                            exceptionNote = ""
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanBarcodeDialog(
    initialCode: String,
    onDismiss: () -> Unit,
    onApply: (String, InventoryStatus, String) -> Unit
) {
    var code by remember { mutableStateOf(initialCode) }
    var status by remember { mutableStateOf(InventoryStatus.LOADED) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan / Update Carton Status") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it }, label = { Text("Barcode / QR text") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        InventoryStatus.PACKED,
                        InventoryStatus.LOADED,
                        InventoryStatus.DELIVERED,
                        InventoryStatus.UNPACKED,
                        InventoryStatus.EXCEPTION,
                        InventoryStatus.MISSING
                    ).forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st.label) })
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Exception / condition note (optional)") })
            }
        },
        confirmButton = {
            Button(onClick = { onApply(code, status, note) }, enabled = code.trim().length >= 2) {
                Text("Update Status")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
