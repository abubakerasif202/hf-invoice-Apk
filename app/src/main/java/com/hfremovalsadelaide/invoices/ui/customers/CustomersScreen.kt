package com.hfremovalsadelaide.invoices.ui.customers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.data.CustomerSummary
import com.hfremovalsadelaide.invoices.data.InvoiceSummary
import com.hfremovalsadelaide.invoices.data.QuoteRecord
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.ui.PlatformUiState
import com.hfremovalsadelaide.invoices.ui.PlatformViewModel
import com.hfremovalsadelaide.invoices.ui.components.EmptyStateCard
import com.hfremovalsadelaide.invoices.ui.components.HfSectionCard
import com.hfremovalsadelaide.invoices.ui.components.StatusBadge
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite

private enum class CrmTab(val label: String) {
    DIRECTORY("Customers"),
    QUOTES("Quote Pipeline"),
    FOLLOW_UPS("Follow-Ups & Portal")
}

/** Counts that have not loaded yet are left out instead of reading as a misleading zero. */
internal fun customerActivityLine(jobs: Int?, invoices: Int?, kind: String): String = listOfNotNull(
    jobs?.let { "$it ${if (it == 1) "job" else "jobs"}" },
    invoices?.let { "$it ${if (it == 1) "invoice" else "invoices"}" },
    kind
).joinToString(" · ")

/** One-shot request from another screen (e.g. dashboard quick actions) to open a CRM dialog on arrival. */
enum class CrmLaunchIntent { ADD_CUSTOMER, CREATE_QUOTE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomersScreen(
    platformState: PlatformUiState,
    platformViewModel: PlatformViewModel,
    allJobs: List<Job>,
    allInvoices: List<InvoiceSummary>,
    modifier: Modifier = Modifier,
    launchIntent: CrmLaunchIntent? = null,
    onLaunchIntentConsumed: () -> Unit = {},
    jobsLoaded: Boolean = true,
    invoicesLoaded: Boolean = true,
    onDownloadQuotePdf: (quoteId: String, revisionId: String?) -> Unit = { _, _ -> },
    onOpenConvertedJob: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val crm = platformState.crm
    val canWrite = platformState.user?.can("crm.manage") == true
    val canReadFinancials = platformState.user?.can("invoices.read") == true
    val canConvertToBooking = canWrite && platformState.user?.can("jobs.assign") == true
    var activeTab by rememberSaveable { mutableStateOf(CrmTab.DIRECTORY) }
    var search by rememberSaveable { mutableStateOf("") }
    var stageFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCustomer by remember { mutableStateOf<CustomerSummary?>(null) }
    var selectedQuoteId by remember { mutableStateOf<String?>(null) }
    var showCustomerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(launchIntent) {
        when (launchIntent) {
            CrmLaunchIntent.ADD_CUSTOMER -> {
                activeTab = CrmTab.DIRECTORY
                if (canWrite) showCustomerDialog = true
            }
            CrmLaunchIntent.CREATE_QUOTE -> {
                activeTab = CrmTab.QUOTES
                if (canWrite) platformViewModel.openQuoteWizard()
            }
            null -> return@LaunchedEffect
        }
        onLaunchIntentConsumed()
    }

    val wizardDraft = platformState.quoteWizardDraft
    if (platformState.showQuoteWizard && wizardDraft != null) {
        BackHandler {
            if (wizardDraft.stepIndex > 0) {
                platformViewModel.updateQuoteWizardDraft { it.copy(stepIndex = it.stepIndex - 1) }
            } else {
                platformViewModel.closeQuoteWizard(clearSavedDraft = false)
            }
        }
        QuotationWizardScreen(
            draft = wizardDraft,
            customers = crm?.customers.orEmpty(),
            allJobs = allJobs,
            allInvoices = allInvoices,
            allQuotes = crm?.quotes.orEmpty(),
            rateCards = platformState.rateCards,
            catalogue = platformState.serviceCatalogue,
            termsVersions = platformState.termsVersions.ifEmpty { crm?.approvedTerms.orEmpty() },
            duplicateMatches = platformState.quoteDuplicateMatches,
            priceResult = platformState.quotePricePreview,
            pricingBusy = platformState.quotePricingBusy,
            loadingModule = platformState.loadingModule,
            onUpdateDraft = platformViewModel::updateQuoteWizardDraft,
            onCheckDuplicates = platformViewModel::checkDuplicateCustomers,
            onRefreshServerPrice = platformViewModel::refreshQuoteServerPrice,
            onSaveDraftToServer = { d -> platformViewModel.saveQuoteWizardDraftToServer(d) },
            onPreviewPdf = { qid -> onDownloadQuotePdf(qid, null) },
            onIssueQuotation = { d, reason ->
                platformViewModel.issueQuoteFromWizard(d, reason) { issuedId ->
                    selectedQuoteId = issuedId
                }
            },
            onClose = { clear -> platformViewModel.closeQuoteWizard(clear) },
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("customers_screen"),
        // The green header already pads for the status bar; a Scaffold inset on top would leave a light strip above it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (canWrite && activeTab != CrmTab.FOLLOW_UPS) {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == CrmTab.DIRECTORY) showCustomerDialog = true
                        else platformViewModel.openQuoteWizard(startFresh = false)
                    },
                    containerColor = ForestGreen,
                    contentColor = PureWhite
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestGreen)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Customers & Quotes",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Directory, Quote Pipeline & Customer Portal",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldAccent
                        )
                    }
                    IconButton(onClick = { platformViewModel.loadCrm() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh CRM", tint = PureWhite)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                CrmTab.entries.forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("crm_tab_${tab.name.lowercase()}")
                    )
                }
            }

            when (activeTab) {
                CrmTab.DIRECTORY -> {
                    val list = crm?.customers.orEmpty().filter {
                        val q = search.trim().lowercase()
                        q.isBlank() ||
                            it.name.lowercase().contains(q) ||
                            it.email.lowercase().contains(q) ||
                            it.phone.lowercase().contains(q) ||
                            it.address.lowercase().contains(q)
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search customers by name, phone, email, address") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("crm_search")
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (list.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Rounded.People,
                                    title = "No customers match",
                                    message = "Add a new customer profile or clear your search filter."
                                )
                            }
                        } else {
                            items(list, key = { it.id }) { customer ->
                                val pastInvoicesCount = allInvoices.count {
                                    it.customerName.equals(customer.name, ignoreCase = true)
                                }
                                val pastJobsCount = allJobs.count {
                                    it.customerName?.equals(customer.name, ignoreCase = true) == true
                                }
                                val returning = jobsLoaded && invoicesLoaded && pastInvoicesCount + pastJobsCount > 1
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCustomer = customer }
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                customer.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (returning) {
                                                StatusBadge(
                                                    label = "Returning",
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                        if (customer.phone.isNotBlank() || customer.email.isNotBlank()) {
                                            Text(
                                                listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (customer.address.isNotBlank()) {
                                            Text(
                                                customer.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            customerActivityLine(
                                                jobs = pastJobsCount.takeIf { jobsLoaded },
                                                invoices = pastInvoicesCount.takeIf { invoicesLoaded },
                                                kind = customer.kind
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                CrmTab.QUOTES -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        FilterChip(selected = stageFilter == null, onClick = { stageFilter = null }, label = { Text("All") })
                        QuoteStage.pipeline.forEach { st ->
                            FilterChip(selected = stageFilter == st.key, onClick = { stageFilter = st.key }, label = { Text(st.label) })
                        }
                    }
                    val quotes = crm?.quotes.orEmpty().filter { stageFilter == null || it.stage == stageFilter }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (canWrite && wizardDraft != null) {
                            item {
                                HfSectionCard(title = "Unfinished Encrypted Quotation Draft") {
                                    Text(
                                        text = "${wizardDraft.customerName.ifBlank { "New customer" }} · ${wizardDraft.segment} · Step ${wizardDraft.stepIndex + 1}/5",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { platformViewModel.openQuoteWizard(startFresh = false) },
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                                        ) { Text("Resume Wizard") }
                                        OutlinedButton(onClick = { platformViewModel.openQuoteWizard(startFresh = true) }) {
                                            Text("Start New")
                                        }
                                    }
                                }
                            }
                        }
                        if (quotes.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Rounded.People,
                                    title = "No quotes in this stage",
                                    message = "Create a quotation using the 5-step wizard (+ button).",
                                    actionLabel = if (canWrite) "Launch Quotation Wizard" else null,
                                    onAction = if (canWrite) ({ platformViewModel.openQuoteWizard(startFresh = true) }) else null
                                )
                            }
                        } else {
                            items(quotes, key = { it.id }) { q ->
                                QuotePipelineCard(
                                    quote = q,
                                    canWrite = canWrite,
                                    canReadFinancials = canReadFinancials,
                                    onOpenDetail = {
                                        selectedQuoteId = q.id
                                        platformViewModel.loadQuoteDetail(q.id)
                                    },
                                    onAdvance = { next -> platformViewModel.advanceQuote(q.id, next) },
                                    onDownloadPdf = { onDownloadQuotePdf(q.id, q.currentRevisionId) },
                                    onGenerateTemplate = { kind ->
                                        platformViewModel.renderTemplate(
                                            kind = kind,
                                            customerName = q.customerName,
                                            reference = q.reference,
                                            amount = q.estimatedTotal,
                                            date = q.preferredDate,
                                            pickup = q.pickup,
                                            delivery = q.delivery,
                                            truck = q.truckClass,
                                            movers = q.movers
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                CrmTab.FOLLOW_UPS -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            HfSectionCard(title = "Customer Portal Review Queue") {
                                val pending = crm?.portalSubmissions.orEmpty()
                                if (pending.isEmpty()) {
                                    Text("No customer portal submissions pending.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    pending.forEach { sub ->
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                "${sub.customerName} · ${sub.kind.replace('_', ' ')} (${sub.status})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(sub.payloadSummary, style = MaterialTheme.typography.bodySmall)
                                            if (sub.status == "pending" && canWrite) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { platformViewModel.reviewPortalSubmission(sub.id, "approved") },
                                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                                                    ) { Text("Approve") }
                                                    OutlinedButton(
                                                        onClick = { platformViewModel.reviewPortalSubmission(sub.id, "rejected") }
                                                    ) { Text("Decline") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            HfSectionCard(title = "Follow-Up Reminders") {
                                val followUps = crm?.followUps.orEmpty()
                                if (followUps.isEmpty()) {
                                    Text("No follow-ups scheduled.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    followUps.forEach { fu ->
                                        Column(Modifier.padding(vertical = 4.dp)) {
                                            Text("${fu.dueDate} · ${fu.customerName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(fu.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            if (fu.notes.isNotBlank()) Text(fu.notes, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedCustomer?.let { cust ->
        CustomerProfileSheet(
            customer = cust,
            jobs = allJobs.filter { it.customerName?.equals(cust.name, ignoreCase = true) == true },
            invoices = if (platformState.user?.can("invoices.read") == true) {
                allInvoices.filter { it.customerName.equals(cust.name, ignoreCase = true) }
            } else emptyList(),
            quotes = crm?.quotes.orEmpty().filter { it.customerId == cust.id },
            canWrite = canWrite,
            canCreatePortalLink = platformState.user?.let { it.can("portal.manage") && it.can("invoices.read") } == true,
            onCreatePortalLink = { platformViewModel.createPortalLink(cust.id) },
            onRenderTemplate = { kind -> platformViewModel.renderTemplate(kind, cust.name, null, null) },
            onDismiss = { selectedCustomer = null }
        )
    }

    if (showCustomerDialog) {
        NewCustomerDialog(
            onDismiss = { showCustomerDialog = false },
            onSave = { name, kind, email, phone, address, notes ->
                showCustomerDialog = false
                platformViewModel.saveCustomer(null, name, kind, email, phone, address, notes)
            }
        )
    }

    val selectedQuote = selectedQuoteId?.let { id -> crm?.quotes.orEmpty().firstOrNull { it.id == id } }
    if (selectedQuote != null) {
        QuoteDetailBottomSheet(
            quote = selectedQuote,
            detail = platformState.activeQuoteDetail?.takeIf { it.quoteId == selectedQuote.id },
            canWrite = canWrite,
            canReadFinancials = canReadFinancials,
            canConvertToBooking = canConvertToBooking,
            onDismiss = { selectedQuoteId = null },
            onRefresh = { platformViewModel.loadQuoteDetail(selectedQuote.id) },
            onDownloadPdf = { revId -> onDownloadQuotePdf(selectedQuote.id, revId) },
            onOpenRevisionWizard = { reason ->
                selectedQuoteId = null
                platformViewModel.openRevisionDraftInWizard(selectedQuote, reason)
            },
            onCloneVariation = { reason -> platformViewModel.cloneQuoteVariation(selectedQuote.id, reason) },
            onAcceptRevision = { revId, method, notes ->
                platformViewModel.acceptQuoteRevision(selectedQuote.id, revId, method, notes)
            },
            onConvertToBooking = {
                platformViewModel.convertQuoteToBooking(selectedQuote.id) { jobId ->
                    selectedQuoteId = null
                    onOpenConvertedJob(jobId)
                }
            },
            onOpenBookingJob = { jobId ->
                selectedQuoteId = null
                onOpenConvertedJob(jobId)
            },
            onRenderTemplate = { kind ->
                platformViewModel.renderTemplate(
                    kind = kind,
                    customerName = selectedQuote.customerName,
                    reference = selectedQuote.reference,
                    amount = selectedQuote.estimatedTotal,
                    date = selectedQuote.preferredDate,
                    pickup = selectedQuote.pickup,
                    delivery = selectedQuote.delivery,
                    revisionNumber = selectedQuote.revisionCount.takeIf { it > 0 },
                    truck = selectedQuote.truckClass,
                    movers = selectedQuote.movers
                )
            }
        )
    }

    val bookingConflictMsg = platformState.quoteBookingConflictMessage
    if (bookingConflictMsg != null && selectedQuote != null) {
        QuoteBookingConflictDialog(
            conflictMessage = bookingConflictMsg,
            canOverride = platformState.user?.can("jobs.override") == true,
            onDismiss = { platformViewModel.dismissQuoteBookingConflict() },
            onConfirmOverride = { reason ->
                platformViewModel.convertQuoteToBooking(selectedQuote.id, reason) { jobId ->
                    selectedQuoteId = null
                    onOpenConvertedJob(jobId)
                }
            }
        )
    }

    platformState.portalLinkPreview?.let { url ->
        AlertDialog(
            onDismissRequest = { platformViewModel.clearPreviews() },
            title = { Text("Customer Portal Link") },
            text = { Text("Share this private, expiring booking portal link explicitly with the customer:\n\n$url") },
            confirmButton = {
                Button(onClick = {
                    shareText(context, "HF Removals Booking Portal", url)
                    platformViewModel.clearPreviews()
                }) { Text("Share Link") }
            },
            dismissButton = {
                TextButton(onClick = {
                    copyText(context, url)
                    platformViewModel.clearPreviews()
                }) { Text("Copy") }
            }
        )
    }

    platformState.renderedTemplate?.let { (subject, body) ->
        AlertDialog(
            onDismissRequest = { platformViewModel.clearPreviews() },
            title = { Text(subject) },
            text = { Text(body) },
            confirmButton = {
                Button(onClick = {
                    shareText(context, subject, "$subject\n\n$body")
                    platformViewModel.clearPreviews()
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = {
                    copyText(context, "$subject\n\n$body")
                    platformViewModel.clearPreviews()
                }) { Text("Copy") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuotePipelineCard(
    quote: QuoteRecord,
    canWrite: Boolean,
    canReadFinancials: Boolean,
    onOpenDetail: () -> Unit,
    onAdvance: (String) -> Unit,
    onDownloadPdf: () -> Unit,
    onGenerateTemplate: (String) -> Unit
) {
    val nextStage = QuoteStage.next(quote.stage)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${quote.reference}${if (quote.revisionCount > 0) " · Rev ${quote.revisionCount}" else ""} · ${quote.customerName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(QuoteStage.labelFor(quote.stage), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (quote.legacyStatus != null) {
                Text(
                    "Legacy record (${quote.legacyStatus}) — snapshot read-only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (quote.duplicateOfCustomerId != null) {
                Text(
                    "Possible duplicate of an existing customer profile — verify before booking.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (quote.pickup.isNotBlank() || quote.delivery.isNotBlank()) {
                Text("${quote.pickup} -> ${quote.delivery}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                buildString {
                    append(quote.segment.replaceFirstChar { it.uppercase() })
                    append(" · ")
                    append(quote.truckClass.ifBlank { "Truck TBC" })
                    if (canReadFinancials) {
                        append(" · Est: ")
                        append(quote.estimatedTotal?.let { "$$it" } ?: "TBC")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onOpenDetail) {
                    Text("Revisions & Actions")
                }
                if (canReadFinancials) {
                    OutlinedButton(onClick = onDownloadPdf) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("PDF")
                    }
                }
                if (canWrite && nextStage != null && quote.stage !in setOf("quote_prepared", "quote_sent", "accepted")) {
                    Button(
                        onClick = { onAdvance(nextStage.key) },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                    ) { Text("Move to ${nextStage.label}") }
                }
                if (canWrite) {
                    OutlinedButton(onClick = { onGenerateTemplate("quote") }) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Template")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CustomerProfileSheet(
    customer: CustomerSummary,
    jobs: List<Job>,
    invoices: List<InvoiceSummary>,
    quotes: List<QuoteRecord>,
    canWrite: Boolean,
    canCreatePortalLink: Boolean,
    onCreatePortalLink: () -> Unit,
    onRenderTemplate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(customer.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("${customer.kind} · ${customer.address.ifBlank { "No address" }}", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (customer.phone.isNotBlank()) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + customer.phone.filter { it.isDigit() || it == '+' })))
                    }) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Call")
                    }
                }
                if (customer.email.isNotBlank()) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${customer.email}")))
                    }) {
                        Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Email")
                    }
                }
                if (canCreatePortalLink) {
                    Button(
                        onClick = onCreatePortalLink,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Portal Link")
                    }
                }
            }

            if (canWrite) {
                Text("Templates (manual review & send)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUOTE_TEMPLATE_KINDS
                        .forEach { (k, label) ->
                            FilterChip(selected = false, onClick = { onRenderTemplate(k) }, label = { Text(label) })
                        }
                }
            }

            HfSectionCard(title = "History (${jobs.size} jobs · ${invoices.size} invoices · ${quotes.size} quotes)") {
                invoices.take(5).forEach { inv ->
                    Text("${inv.number} · ${inv.status.serverLabel} · ${inv.formattedTotal}", style = MaterialTheme.typography.bodySmall)
                }
                jobs.take(5).forEach { j ->
                    Text("${j.serviceDate ?: ""} · ${j.title} (${j.state.server})", style = MaterialTheme.typography.bodySmall)
                }
                if (customer.notes.isNotBlank()) {
                    Text("Notes: ${customer.notes}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NewCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Individual") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Customer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Individual", "Business").forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), kind, email.trim(), phone.trim(), address.trim(), notes.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuoteDetailBottomSheet(
    quote: QuoteRecord,
    detail: com.hfremovalsadelaide.invoices.data.QuoteDetailPayload?,
    canWrite: Boolean,
    canReadFinancials: Boolean,
    canConvertToBooking: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadPdf: (revisionId: String?) -> Unit,
    onOpenRevisionWizard: (reason: String) -> Unit,
    onCloneVariation: (reason: String) -> Unit,
    onAcceptRevision: (revisionId: String, method: String, notes: String) -> Unit,
    onConvertToBooking: () -> Unit,
    onOpenBookingJob: (String) -> Unit,
    onRenderTemplate: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var revisionReason by remember { mutableStateOf("") }
    var acceptTargetRevId by remember { mutableStateOf<String?>(null) }
    var acceptMethod by remember { mutableStateOf("email") }
    var acceptNotes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(quote.reference, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("${quote.customerName} · ${quote.segment}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh quote detail") }
            }

            if (quote.legacyStatus != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Legacy snapshot (${quote.legacyStatus}) — preserved without silent repricing.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canReadFinancials) {
                    OutlinedButton(onClick = { onDownloadPdf(quote.currentRevisionId) }) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Quote PDF")
                    }
                }
                if (quote.jobId != null) {
                    Button(
                        onClick = { onOpenBookingJob(quote.jobId) },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                    ) {
                        Icon(Icons.Rounded.EventAvailable, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Open Booking")
                    }
                } else if (canConvertToBooking && quote.stage == "accepted") {
                    Button(
                        onClick = onConvertToBooking,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                    ) {
                        Icon(Icons.Rounded.EventAvailable, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Convert to Booking")
                    }
                }
            }

            if (canWrite && canReadFinancials) {
                HfSectionCard(title = if (quote.acceptedRevisionId != null) "Post-Acceptance Variation" else "Revise Quotation") {
                    OutlinedTextField(
                        value = revisionReason,
                        onValueChange = { revisionReason = it },
                        label = { Text("Reason for change (min 3 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (quote.acceptedRevisionId != null) {
                        OutlinedButton(
                            enabled = revisionReason.trim().length >= 3,
                            onClick = { onCloneVariation(revisionReason.trim()) }
                        ) { Text("Clone Variation Quote (Preserves Accepted)") }
                    } else {
                        OutlinedButton(
                            enabled = revisionReason.trim().length >= 3,
                            onClick = { onOpenRevisionWizard(revisionReason.trim()) }
                        ) {
                            Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Create New Revision in Wizard")
                        }
                    }
                }
            }

            HfSectionCard(title = "Issued Versions (${detail?.revisions?.size ?: quote.revisionCount})") {
                val revs = detail?.revisions.orEmpty()
                if (revs.isEmpty()) {
                    Text("No issued revisions recorded yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    revs.forEach { rev ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Revision #${rev.revisionNumber}", fontWeight = FontWeight.Bold)
                                    StatusBadge(rev.status, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                if (canReadFinancials && rev.total != null) {
                                    Text("${if (rev.isEstimate) "Est." else "Fixed"} $${rev.total} · Valid until ${rev.validUntil ?: "-"}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (rev.termsTitle != null) {
                                    Text("Terms: v${rev.termsVersionNumber ?: 1} · ${rev.termsTitle}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (rev.acceptedAt != null) {
                                    Text("Accepted (${rev.acceptanceMethod}) by ${rev.acceptedByStaff ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (canReadFinancials) {
                                        OutlinedButton(onClick = { onDownloadPdf(rev.id) }) { Text("PDF") }
                                    }
                                    if (canWrite && canReadFinancials && rev.status == "issued" && quote.acceptedRevisionId == null) {
                                        Button(
                                            onClick = { acceptTargetRevId = rev.id },
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                                        ) {
                                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Record Acceptance")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (canWrite) {
                Text("Communication Templates (manual review & send)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QUOTE_TEMPLATE_KINDS
                        .filter { canReadFinancials || (it.first != "payment_request" && it.first != "deposit_receipt") }
                        .forEach { (k, label) ->
                            FilterChip(selected = false, onClick = { onRenderTemplate(k) }, label = { Text(label) })
                        }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    acceptTargetRevId?.let { revId ->
        AlertDialog(
            onDismissRequest = { acceptTargetRevId = null },
            title = { Text("Record Customer Acceptance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Acceptance locks this exact quotation revision cleanly.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ACCEPTANCE_METHODS.forEach { (k, lbl) ->
                            FilterChip(selected = acceptMethod == k, onClick = { acceptMethod = k }, label = { Text(lbl) })
                        }
                    }
                    OutlinedTextField(
                        value = acceptNotes,
                        onValueChange = { acceptNotes = it },
                        label = { Text("Acceptance notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        acceptTargetRevId = null
                        onAcceptRevision(revId, acceptMethod, acceptNotes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                ) { Text("Confirm Acceptance") }
            },
            dismissButton = { TextButton(onClick = { acceptTargetRevId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuoteBookingConflictDialog(
    conflictMessage: String,
    canOverride: Boolean,
    onDismiss: () -> Unit,
    onConfirmOverride: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scheduling Conflict Detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(conflictMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (canOverride) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Authorised override reason (min 5 chars)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (canOverride) {
                Button(
                    enabled = reason.trim().length >= 5,
                    onClick = { onConfirmOverride(reason.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                ) { Text("Override & Book") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("HF Removals", text))
}
