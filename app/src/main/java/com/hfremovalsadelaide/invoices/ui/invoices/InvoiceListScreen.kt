package com.hfremovalsadelaide.invoices.ui.invoices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.data.InvoiceStatus
import com.hfremovalsadelaide.invoices.data.InvoiceSummary
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.ui.components.HfConfirmDialog
import com.hfremovalsadelaide.invoices.ui.components.SkeletonListPlaceholder
import com.hfremovalsadelaide.invoices.ui.theme.ErrorContainerTint
import com.hfremovalsadelaide.invoices.ui.theme.ErrorCrimson
import com.hfremovalsadelaide.invoices.ui.theme.ForestActive
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.ForestSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.GoldDeep
import com.hfremovalsadelaide.invoices.ui.theme.GoldSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.MutedSage
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.ui.theme.SageOutline
import com.hfremovalsadelaide.invoices.ui.theme.SageSurface
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    uiState: InvoicesUiState,
    savedDocumentsCount: Int,
    onRefresh: (isPullRefresh: Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterSelected: (InvoiceStatus?) -> Unit,
    onCreateInvoiceClick: () -> Unit,
    onGoToSignInClick: () -> Unit,
    onDownloadPdfClick: (InvoiceSummary) -> Unit,
    onPrintPdfClick: (InvoiceSummary) -> Unit,
    onSavedDocumentsClick: () -> Unit,
    onNavigateBackToWorkspace: () -> Unit,
    onSelectInvoice: (InvoiceSummary?) -> Unit = {},
    onRecordPayment: (invoiceId: String, amount: String, date: String, reference: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    onMarkPaid: (invoiceId: String, date: String, reference: String) -> Unit = { _, _, _ -> },
    onVoidInvoice: (invoiceId: String) -> Unit = {},
    onDuplicateInvoice: (invoiceId: String) -> Unit = {},
    canWriteInvoices: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBackToWorkspace()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("invoice_list_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            InvoicesTopBar(
                savedDocumentsCount = savedDocumentsCount,
                onSavedDocumentsClick = onSavedDocumentsClick,
                onRefreshClick = { onRefresh(false) }
            )
        },
        floatingActionButton = {
            if (canWriteInvoices && uiState.loadState is InvoicesLoadState.Loaded) {
                FloatingActionButton(
                    onClick = onCreateInvoiceClick,
                    containerColor = ForestGreen,
                    contentColor = PureWhite,
                    modifier = Modifier.testTag("invoices_fab_create")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(id = R.string.invoices_fab_create))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val loadState = uiState.loadState) {
                InvoicesLoadState.Loading -> SkeletonListPlaceholder(count = 4)

                InvoicesLoadState.AuthRequired -> {
                    InvoicesAuthRequiredCard(
                        onSignInClick = onGoToSignInClick,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is InvoicesLoadState.Error -> {
                    InvoicesErrorCard(
                        message = loadState.detail ?: stringResource(id = loadState.messageResId),
                        onRetry = { onRefresh(false) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is InvoicesLoadState.Loaded -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { onRefresh(true) },
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 860.dp)
                    ) {
                        val filtered = uiState.filteredInvoices

                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("invoices_lazy_column")
                        ) {
                            item(key = "metrics_summary") {
                                InvoicesMetricsBanner(
                                    totalCount = uiState.allInvoices.size,
                                    outstandingCount = uiState.outstandingCount,
                                    paidCount = uiState.paidCount
                                )
                            }

                            item(key = "search_and_filters") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = uiState.searchQuery,
                                        onValueChange = onSearchQueryChanged,
                                        singleLine = true,
                                        placeholder = {
                                            Text(
                                                text = stringResource(id = R.string.invoices_search_placeholder),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            if (uiState.searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { onSearchQueryChanged("") },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = stringResource(id = R.string.invoices_clear_search_description),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        shape = MaterialTheme.shapes.medium,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedBorderColor = ForestGreen,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("invoices_search_input")
                                    )

                                    StatusFilterBar(
                                        selectedStatus = uiState.selectedStatusFilter,
                                        onSelectStatus = onStatusFilterSelected
                                    )
                                }
                            }

                            if (filtered.isEmpty()) {
                                item(key = "empty_state") {
                                    EmptyInvoicesCard(onCreateInvoiceClick = onCreateInvoiceClick)
                                }
                            } else {
                                items(items = filtered, key = { it.id }) { invoice ->
                                    InvoiceRecordCard(
                                        invoice = invoice,
                                        onClick = { onSelectInvoice(invoice) },
                                        onDownloadPdf = { onDownloadPdfClick(invoice) },
                                        onPrintPdf = { onPrintPdfClick(invoice) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.selectedInvoice?.let { inv ->
        InvoiceDetailSheet(
            invoice = inv,
            busy = uiState.actionBusy,
            canWrite = canWriteInvoices,
            onDownloadPdf = { onDownloadPdfClick(inv) },
            onPrintPdf = { onPrintPdfClick(inv) },
            onRecordPayment = { amount, date, ref, notes -> onRecordPayment(inv.id, amount, date, ref, notes) },
            onMarkPaid = { date, ref -> onMarkPaid(inv.id, date, ref) },
            onVoidInvoice = { onVoidInvoice(inv.id) },
            onDuplicateInvoice = { onDuplicateInvoice(inv.id) },
            onDismiss = { onSelectInvoice(null) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InvoiceDetailSheet(
    invoice: InvoiceSummary,
    busy: Boolean,
    canWrite: Boolean,
    onDownloadPdf: () -> Unit,
    onPrintPdf: () -> Unit,
    onRecordPayment: (String, String, String, String) -> Unit,
    onMarkPaid: (String, String) -> Unit,
    onVoidInvoice: () -> Unit,
    onDuplicateInvoice: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPaymentDialog by remember { mutableStateOf(false) }
    var confirmVoid by remember { mutableStateOf(false) }
    val todayStr = remember { LocalDate.now(JobFormat.zone).toString() }

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
                    Text(invoice.number, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(invoice.customerName, style = MaterialTheme.typography.titleSmall)
                }
                Text(invoice.status.serverLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            if (invoice.pickup.isNotBlank() || invoice.delivery.isNotBlank()) {
                Text("${invoice.pickup} -> ${invoice.delivery}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailAmountRow("Total (incl GST)", invoice.formattedTotal)
                    DetailAmountRow("GST", invoice.formattedGst)
                    DetailAmountRow("Paid", invoice.formattedPaid)
                    DetailAmountRow("Outstanding Balance", invoice.formattedBalance)
                    DetailAmountRow("Issued / Due", "${invoice.issuedDate} / ${invoice.dueDate}")
                }
            }
            if (invoice.payments.isNotEmpty()) {
                Text("Payments Recorded", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                invoice.payments.forEach { p ->
                    Text("• ${p.date}: $${p.amount} (${p.reference.ifBlank { "Payment" }})", style = MaterialTheme.typography.bodySmall)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDownloadPdf) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("PDF")
                }
                OutlinedButton(onClick = onPrintPdf) {
                    Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Print")
                }
                if (canWrite) {
                    OutlinedButton(onClick = onDuplicateInvoice, enabled = !busy) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Duplicate")
                    }
                    if (invoice.status != InvoiceStatus.PAID && invoice.status != InvoiceStatus.VOID) {
                        Button(
                            onClick = { showPaymentDialog = true },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Record Payment")
                        }
                        OutlinedButton(onClick = { onMarkPaid(todayStr, "") }, enabled = !busy) {
                            Text("Mark Paid")
                        }
                        TextButton(onClick = { confirmVoid = true }, enabled = !busy) {
                            Text("Void", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showPaymentDialog) {
        var amount by remember { mutableStateOf(invoice.balanceAmount.toPlainString()) }
        var date by remember { mutableStateOf(todayStr) }
        var reference by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Record Payment (${invoice.number})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount AUD") }, singleLine = true)
                    OutlinedTextField(date, { date = it }, label = { Text("Date (yyyy-MM-dd)") }, singleLine = true)
                    OutlinedTextField(reference, { reference = it }, label = { Text("Reference / EFT") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                Button(onClick = { showPaymentDialog = false; onRecordPayment(amount, date, reference, notes) }, enabled = amount.isNotBlank()) {
                    Text("Save Payment")
                }
            },
            dismissButton = { TextButton(onClick = { showPaymentDialog = false }) { Text("Cancel") } }
        )
    }

    if (confirmVoid) {
        HfConfirmDialog(
            title = "Void ${invoice.number}?",
            message = "This marks the invoice Void in the immutable ledger and removes it from receivable balances.",
            confirmLabel = "Void Invoice",
            isDestructive = true,
            onConfirm = { confirmVoid = false; onVoidInvoice() },
            onDismiss = { confirmVoid = false }
        )
    }
}

@Composable
private fun DetailAmountRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InvoicesTopBar(
    savedDocumentsCount: Int,
    onSavedDocumentsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Surface(
        color = ForestCanopy,
        contentColor = PureWhite,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 16.dp)
            ) {
                val isWide = maxWidth >= 600.dp

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PureWhite,
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.8f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.hf_logo),
                            contentDescription = stringResource(id = R.string.splash_logo_description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.invoices_screen_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isWide) {
                            Text(
                                text = stringResource(id = R.string.invoices_screen_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onSavedDocumentsClick,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("invoices_saved_pdfs_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (savedDocumentsCount > 0) {
                                    Badge(containerColor = GoldAccent, contentColor = ForestCanopy) {
                                        Text(
                                            text = if (savedDocumentsCount > 9) "9+" else savedDocumentsCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(id = R.string.nav_downloads_description), tint = PureWhite)
                        }
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("invoices_refresh_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(id = R.string.nav_refresh_description), tint = PureWhite)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(GoldAccent.copy(alpha = 0.65f))
            )
        }
    }
}

@Composable
private fun InvoicesMetricsBanner(totalCount: Int, outstandingCount: Int, paidCount: Int) {
    Surface(
        shape = MaterialTheme.shapes.large,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth().testTag("invoices_metrics_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(colors = listOf(ForestCanopy, ForestGreen, ForestActive)))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MetricColumn(stringResource(id = R.string.invoices_metric_total), totalCount.toString(), PureWhite)
                Box(Modifier.width(1.dp).height(34.dp).background(PureWhite.copy(alpha = 0.18f)))
                MetricColumn(stringResource(id = R.string.invoices_metric_outstanding), outstandingCount.toString(), GoldAccent)
                Box(Modifier.width(1.dp).height(34.dp).background(PureWhite.copy(alpha = 0.18f)))
                MetricColumn(stringResource(id = R.string.invoices_metric_paid), paidCount.toString(), Color(0xFFB5E6CD))
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, color = accentColor)
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = PureWhite.copy(alpha = 0.8f))
    }
}

@Composable
private fun StatusFilterBar(selectedStatus: InvoiceStatus?, onSelectStatus: (InvoiceStatus?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("invoices_status_filter_row")
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onSelectStatus(null) },
            label = { Text(stringResource(id = R.string.invoices_filter_all)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = ForestGreen,
                selectedLabelColor = PureWhite,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.primary
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedStatus == null,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = ForestGreen
            ),
            modifier = Modifier.testTag("filter_chip_all")
        )

        InvoiceStatus.entries.forEach { status ->
            val selected = selectedStatus == status
            FilterChip(
                selected = selected,
                onClick = { onSelectStatus(if (selected) null else status) },
                label = { Text(status.serverLabel) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ForestGreen,
                    selectedLabelColor = PureWhite,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = ForestGreen
                ),
                modifier = Modifier.testTag("filter_chip_${status.name.lowercase()}")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvoiceRecordCard(
    invoice: InvoiceSummary,
    onClick: () -> Unit,
    onDownloadPdf: () -> Unit,
    onPrintPdf: () -> Unit
) {
    val badgeColors = statusBadgeColors(invoice.status)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("invoice_card_${invoice.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(3.5.dp).background(badgeColors.accentStrip))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = invoice.number,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = badgeColors.container, border = BorderStroke(1.dp, badgeColors.border)) {
                        Text(invoice.status.serverLabel, style = MaterialTheme.typography.labelMedium, color = badgeColors.content, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(invoice.customerName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (invoice.servicesDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(invoice.servicesDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = GoldDeep, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (invoice.issuedDate.isNotBlank()) stringResource(id = R.string.invoices_issued_prefix, invoice.issuedDate) else "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (invoice.dueDate.isNotBlank()) {
                        Text(
                            text = stringResource(id = R.string.invoices_due_prefix, invoice.dueDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (invoice.status == InvoiceStatus.OVERDUE) ErrorCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(invoice.formattedTotal, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        if (invoice.status == InvoiceStatus.PARTIALLY_PAID || invoice.status == InvoiceStatus.OVERDUE) {
                            Text(
                                text = "Balance ${invoice.formattedBalance}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (invoice.status == InvoiceStatus.OVERDUE) ErrorCrimson else GoldDeep
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDownloadPdf,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("invoice_pdf_btn_${invoice.id}")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(id = R.string.invoices_action_pdf), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = onPrintPdf,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, GoldAccent),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = GoldSoftTint, contentColor = GoldDeep),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("invoice_print_btn_${invoice.id}")
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(id = R.string.invoices_action_print), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

private data class StatusBadgePalette(
    val container: Color,
    val content: Color,
    val border: Color,
    val accentStrip: Color
)

private fun statusBadgeColors(status: InvoiceStatus): StatusBadgePalette = when (status) {
    InvoiceStatus.PAID -> StatusBadgePalette(ForestSoftTint, ForestGreen, ForestGreen.copy(alpha = 0.3f), ForestGreen)
    InvoiceStatus.ISSUED -> StatusBadgePalette(GoldSoftTint, GoldDeep, GoldAccent, GoldAccent)
    InvoiceStatus.PARTIALLY_PAID -> StatusBadgePalette(GoldSoftTint, GoldDeep, GoldDeep.copy(alpha = 0.45f), GoldDeep)
    InvoiceStatus.OVERDUE -> StatusBadgePalette(ErrorContainerTint, ErrorCrimson, ErrorCrimson.copy(alpha = 0.4f), ErrorCrimson)
    InvoiceStatus.VOID -> StatusBadgePalette(Color(0xFFECEFED), MutedSage, SageOutline, MutedSage)
    InvoiceStatus.DRAFT -> StatusBadgePalette(SageSurface, ForestActive, SageOutline, ForestActive)
}

@Composable
private fun EmptyInvoicesCard(onCreateInvoiceClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("invoices_empty_card")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp)) {
            Surface(shape = CircleShape, color = ForestSoftTint, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(stringResource(id = R.string.invoices_empty_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(6.dp))
            Text(stringResource(id = R.string.invoices_empty_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCreateInvoiceClick,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.invoices_fab_create))
            }
        }
    }
}

@Composable
private fun InvoicesAuthRequiredCard(onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp).testTag("invoices_auth_required_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(Brush.horizontalGradient(listOf(ForestGreen, GoldAccent, ForestGreen))))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp)) {
                    Image(painterResource(id = R.drawable.hf_logo), contentDescription = stringResource(id = R.string.splash_logo_description), contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(shape = CircleShape, color = GoldSoftTint, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, contentDescription = null, tint = GoldDeep, modifier = Modifier.size(24.dp)) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(id = R.string.invoices_auth_required_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(id = R.string.invoices_auth_required_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onSignInClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).minimumInteractiveComponentSize().testTag("invoices_sign_in_button")
                    ) { Text(stringResource(id = R.string.invoices_go_to_sign_in), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
private fun InvoicesErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                Surface(shape = CircleShape, color = ErrorContainerTint, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(26.dp)) }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(id = R.string.invoices_error_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).minimumInteractiveComponentSize().testTag("invoices_retry_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.action_retry))
                }
            }
        }
    }
}
