package com.hfremovalsadelaide.invoices.ui.invoices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBackToWorkspace()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("invoice_list_screen"),
        // The shell's NavigationBar already consumes the system navigation-bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = SageSurface,
        topBar = {
            InvoicesTopBar(
                savedDocumentsCount = savedDocumentsCount,
                onSavedDocumentsClick = onSavedDocumentsClick,
                onRefreshClick = { onRefresh(false) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val loadState = uiState.loadState) {
                InvoicesLoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ForestGreen,
                                trackColor = SageOutline
                            )
                            Text(
                                text = stringResource(id = R.string.splash_connecting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSage
                            )
                        }
                    }
                }

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
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 24.dp
                            ),
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
                                                tint = ForestGreen
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
                                                        contentDescription = stringResource(
                                                            id = R.string.invoices_clear_search_description
                                                        ),
                                                        tint = MutedSage
                                                    )
                                                }
                                            }
                                        },
                                        shape = MaterialTheme.shapes.medium,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = PureWhite,
                                            unfocusedContainerColor = PureWhite,
                                            focusedBorderColor = ForestGreen,
                                            unfocusedBorderColor = SageOutline
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
                                    EmptyInvoicesCard(
                                        onCreateInvoiceClick = onCreateInvoiceClick
                                    )
                                }
                            } else {
                                items(
                                    items = filtered,
                                    key = { it.id }
                                ) { invoice ->
                                    InvoiceRecordCard(
                                        invoice = invoice,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
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
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("invoices_saved_pdfs_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (savedDocumentsCount > 0) {
                                    Badge(
                                        containerColor = GoldAccent,
                                        contentColor = ForestCanopy
                                    ) {
                                        Text(
                                            text = if (savedDocumentsCount > 9) "9+" else savedDocumentsCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = stringResource(id = R.string.nav_downloads_description),
                                tint = PureWhite
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("invoices_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.nav_refresh_description),
                            tint = PureWhite
                        )
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
private fun InvoicesMetricsBanner(
    totalCount: Int,
    outstandingCount: Int,
    paidCount: Int
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invoices_metrics_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ForestCanopy, ForestGreen, ForestActive)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricColumn(
                    label = stringResource(id = R.string.invoices_metric_total),
                    value = totalCount.toString(),
                    accentColor = PureWhite
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(PureWhite.copy(alpha = 0.18f))
                )
                MetricColumn(
                    label = stringResource(id = R.string.invoices_metric_outstanding),
                    value = outstandingCount.toString(),
                    accentColor = GoldAccent
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(PureWhite.copy(alpha = 0.18f))
                )
                MetricColumn(
                    label = stringResource(id = R.string.invoices_metric_paid),
                    value = paidCount.toString(),
                    accentColor = Color(0xFFB5E6CD)
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    accentColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accentColor
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PureWhite.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun StatusFilterBar(
    selectedStatus: InvoiceStatus?,
    onSelectStatus: (InvoiceStatus?) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("invoices_status_filter_row")
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onSelectStatus(null) },
            label = { Text(stringResource(id = R.string.invoices_filter_all)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = ForestGreen,
                selectedLabelColor = PureWhite,
                containerColor = PureWhite,
                labelColor = ForestGreen
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedStatus == null,
                borderColor = SageOutline,
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
                    containerColor = PureWhite,
                    labelColor = ForestGreen
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = SageOutline,
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
    onDownloadPdf: () -> Unit,
    onPrintPdf: () -> Unit
) {
    val badgeColors = statusBadgeColors(invoice.status)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = PureWhite,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SageOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invoice_card_${invoice.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(badgeColors.accentStrip)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = invoice.number,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ForestGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = badgeColors.container,
                        border = BorderStroke(1.dp, badgeColors.border)
                    ) {
                        Text(
                            text = invoice.status.serverLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColors.content,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = invoice.customerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (invoice.servicesDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = invoice.servicesDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSage,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = GoldDeep,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (invoice.issuedDate.isNotBlank()) {
                                stringResource(id = R.string.invoices_issued_prefix, invoice.issuedDate)
                            } else {
                                "—"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSage
                        )
                    }

                    if (invoice.dueDate.isNotBlank()) {
                        Text(
                            text = stringResource(id = R.string.invoices_due_prefix, invoice.dueDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (invoice.status == InvoiceStatus.OVERDUE) ErrorCrimson else MutedSage
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SageOutline.copy(alpha = 0.75f))
                Spacer(modifier = Modifier.height(10.dp))

                // FlowRow: amounts and actions share a line when there is room, and the actions wrap
                // onto their own line at large font sizes instead of squeezing the labels.
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = invoice.formattedTotal,
                            style = MaterialTheme.typography.titleLarge,
                            color = ForestCanopy
                        )
                        if (invoice.status == InvoiceStatus.PARTIALLY_PAID ||
                            invoice.status == InvoiceStatus.OVERDUE
                        ) {
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
                            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("invoice_pdf_btn_${invoice.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.invoices_action_pdf),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = onPrintPdf,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, GoldAccent),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = GoldSoftTint,
                                contentColor = GoldDeep
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("invoice_print_btn_${invoice.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Print,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.invoices_action_print),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
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
    InvoiceStatus.PAID -> StatusBadgePalette(
        container = ForestSoftTint,
        content = ForestGreen,
        border = ForestGreen.copy(alpha = 0.3f),
        accentStrip = ForestGreen
    )
    InvoiceStatus.ISSUED -> StatusBadgePalette(
        container = GoldSoftTint,
        content = GoldDeep,
        border = GoldAccent,
        accentStrip = GoldAccent
    )
    InvoiceStatus.PARTIALLY_PAID -> StatusBadgePalette(
        container = GoldSoftTint,
        content = GoldDeep,
        border = GoldDeep.copy(alpha = 0.45f),
        accentStrip = GoldDeep
    )
    InvoiceStatus.OVERDUE -> StatusBadgePalette(
        container = ErrorContainerTint,
        content = ErrorCrimson,
        border = ErrorCrimson.copy(alpha = 0.4f),
        accentStrip = ErrorCrimson
    )
    InvoiceStatus.VOID -> StatusBadgePalette(
        container = Color(0xFFECEFED),
        content = MutedSage,
        border = SageOutline,
        accentStrip = MutedSage
    )
    InvoiceStatus.DRAFT -> StatusBadgePalette(
        container = SageSurface,
        content = ForestActive,
        border = SageOutline,
        accentStrip = ForestActive
    )
}

@Composable
private fun EmptyInvoicesCard(
    onCreateInvoiceClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = PureWhite,
        border = BorderStroke(1.dp, SageOutline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("invoices_empty_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ForestSoftTint,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(id = R.string.invoices_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.invoices_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSage,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCreateInvoiceClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestGreen,
                    contentColor = PureWhite
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(id = R.string.invoices_fab_create))
            }
        }
    }
}

@Composable
private fun InvoicesAuthRequiredCard(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = PureWhite,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, SageOutline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("invoices_auth_required_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            Brush.horizontalGradient(listOf(ForestGreen, GoldAccent, ForestGreen))
                        )
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hf_logo),
                        contentDescription = stringResource(id = R.string.splash_logo_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = GoldSoftTint,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = GoldDeep,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.invoices_auth_required_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.invoices_auth_required_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSage,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onSignInClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = PureWhite
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("invoices_sign_in_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.invoices_go_to_sign_in),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoicesErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = PureWhite,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, SageOutline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ErrorContainerTint,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = ErrorCrimson,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(id = R.string.invoices_error_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSage,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = PureWhite
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("invoices_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.action_retry))
                }
            }
        }
    }
}
