package com.hfremovalsadelaide.invoices.ui.customers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.data.CustomerSummary
import com.hfremovalsadelaide.invoices.data.DuplicateCustomerMatch
import com.hfremovalsadelaide.invoices.data.InvoiceSummary
import com.hfremovalsadelaide.invoices.data.PricingAdminLogic
import com.hfremovalsadelaide.invoices.data.PricingSegment
import com.hfremovalsadelaide.invoices.data.QuotePriceResult
import com.hfremovalsadelaide.invoices.data.QuoteRecord
import com.hfremovalsadelaide.invoices.data.QuoteSelectedServiceDraft
import com.hfremovalsadelaide.invoices.data.QuoteServiceDayDraft
import com.hfremovalsadelaide.invoices.data.QuoteWizardDraft
import com.hfremovalsadelaide.invoices.data.QuoteWizardStop
import com.hfremovalsadelaide.invoices.data.RateCard
import com.hfremovalsadelaide.invoices.data.ServiceCatalogueItem
import com.hfremovalsadelaide.invoices.data.ServicePricingStatus
import com.hfremovalsadelaide.invoices.data.TermsVersionRecord
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.ui.components.EmptyStateCard
import com.hfremovalsadelaide.invoices.ui.components.HfSectionCard
import com.hfremovalsadelaide.invoices.ui.components.StatusBadge
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import java.time.LocalDate

private val WIZARD_STEPS = listOf(
    "Customer",
    "Moving",
    "Truck & Crew",
    "Services",
    "Review & Issue"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotationWizardScreen(
    draft: QuoteWizardDraft,
    customers: List<CustomerSummary>,
    allJobs: List<Job>,
    allInvoices: List<InvoiceSummary>,
    allQuotes: List<QuoteRecord>,
    rateCards: List<RateCard>,
    catalogue: List<ServiceCatalogueItem>,
    termsVersions: List<TermsVersionRecord>,
    duplicateMatches: List<DuplicateCustomerMatch>,
    priceResult: QuotePriceResult?,
    pricingBusy: Boolean,
    loadingModule: Boolean,
    onUpdateDraft: ((QuoteWizardDraft) -> QuoteWizardDraft) -> Unit,
    onCheckDuplicates: (name: String, email: String, phone: String) -> Unit,
    onRefreshServerPrice: (QuoteWizardDraft) -> Unit,
    onSaveDraftToServer: (QuoteWizardDraft) -> Unit,
    onPreviewPdf: (quoteId: String) -> Unit,
    onIssueQuotation: (QuoteWizardDraft, reason: String) -> Unit,
    onClose: (clearDraft: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now().toString() }
    val approvedTermsForSegment = remember(termsVersions, draft.segment, today) {
        termsVersions.filter {
            it.status == "approved" &&
                (it.segment == null || it.segment == draft.segment) &&
                it.effectiveFrom <= today &&
                (it.effectiveTo == null || it.effectiveTo >= today)
        }
    }

    LaunchedEffect(draft.stepIndex, draft.segment, draft.pricingMode) {
        if (draft.stepIndex == 4) {
            onRefreshServerPrice(draft)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("quotation_wizard_screen"),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestGreen)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onClose(false) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close wizard", tint = PureWhite)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (draft.editingQuoteId != null) "Revise Quotation" else "New Quotation Wizard",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Step ${draft.stepIndex + 1} of 5 · ${WIZARD_STEPS[draft.stepIndex]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldAccent
                        )
                    }
                    TextButton(onClick = { onClose(true) }) {
                        Text("Discard", color = PureWhite)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (draft.stepIndex + 1) / 5f },
                    color = GoldAccent,
                    trackColor = PureWhite.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WIZARD_STEPS.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = draft.stepIndex == idx,
                            onClick = { onUpdateDraft { it.copy(stepIndex = idx) } },
                            label = { Text("${idx + 1}. $label", maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldAccent,
                                selectedLabelColor = ForestGreen,
                                containerColor = PureWhite.copy(alpha = 0.14f),
                                labelColor = PureWhite
                            ),
                            modifier = Modifier.testTag("wizard_step_chip_$idx")
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${draft.segment.replaceFirstChar { it.uppercase() }} · ${if (draft.pricingMode == "fixed_price") "Fixed Price" else "${draft.serviceDays.size} service day(s)"}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    pricingBusy -> "Calculating server total…"
                                    priceResult != null -> buildString {
                                        append(if (priceResult.isEstimate) "Est. total " else "Fixed total ")
                                        append("$${priceResult.total}")
                                        if (priceResult.issues.isNotEmpty()) append(" · ${priceResult.issues.size} warning(s)")
                                    }
                                    else -> "Server price calculated on review / refresh"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (priceResult?.issues?.isNotEmpty() == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("wizard_persistent_summary")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (draft.stepIndex > 0) {
                                OutlinedButton(onClick = { onUpdateDraft { it.copy(stepIndex = (it.stepIndex - 1).coerceAtLeast(0)) } }) {
                                    Text("Back")
                                }
                            }
                            if (draft.stepIndex < 4) {
                                Button(
                                    onClick = { onUpdateDraft { it.copy(stepIndex = (it.stepIndex + 1).coerceAtMost(4)) } },
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                                    modifier = Modifier.testTag("wizard_next_btn")
                                ) {
                                    Text("Next")
                                    Spacer(Modifier.size(4.dp))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (draft.stepIndex) {
                0 -> item {
                    WizardStepCustomer(
                        draft = draft,
                        customers = customers,
                        allJobs = allJobs,
                        allInvoices = allInvoices,
                        allQuotes = allQuotes,
                        duplicateMatches = duplicateMatches,
                        onUpdateDraft = onUpdateDraft,
                        onCheckDuplicates = onCheckDuplicates
                    )
                }
                1 -> item {
                    WizardStepMovingDetails(
                        draft = draft,
                        today = today,
                        onUpdateDraft = onUpdateDraft
                    )
                }
                2 -> item {
                    WizardStepTruckAndCrew(
                        draft = draft,
                        today = today,
                        rateCards = rateCards,
                        onUpdateDraft = onUpdateDraft
                    )
                }
                3 -> item {
                    WizardStepServices(
                        draft = draft,
                        catalogue = catalogue,
                        onUpdateDraft = onUpdateDraft
                    )
                }
                4 -> item {
                    WizardStepReviewAndIssue(
                        draft = draft,
                        priceResult = priceResult,
                        approvedTerms = approvedTermsForSegment,
                        pricingBusy = pricingBusy,
                        loadingModule = loadingModule,
                        onUpdateDraft = onUpdateDraft,
                        onRefreshServerPrice = { onRefreshServerPrice(draft) },
                        onSaveDraft = { onSaveDraftToServer(draft) },
                        onPreviewPdf = onPreviewPdf,
                        onIssueQuotation = { reason -> onIssueQuotation(draft, reason) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardStepCustomer(
    draft: QuoteWizardDraft,
    customers: List<CustomerSummary>,
    allJobs: List<Job>,
    allInvoices: List<InvoiceSummary>,
    allQuotes: List<QuoteRecord>,
    duplicateMatches: List<DuplicateCustomerMatch>,
    onUpdateDraft: ((QuoteWizardDraft) -> QuoteWizardDraft) -> Unit,
    onCheckDuplicates: (String, String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var creatingNew by remember { mutableStateOf(draft.customerId == null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HfSectionCard(title = "Step 1 · Select or Create Customer") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !creatingNew && draft.customerId != null,
                    onClick = { creatingNew = false },
                    label = { Text("Existing customer") }
                )
                FilterChip(
                    selected = creatingNew || draft.customerId == null,
                    onClick = {
                        creatingNew = true
                        onUpdateDraft { it.copy(customerId = null) }
                    },
                    label = { Text("New customer") }
                )
            }

            if (!creatingNew) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search existing customers") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val matches = customers.filter { c ->
                    val q = searchQuery.trim().lowercase()
                    q.isBlank() || c.name.lowercase().contains(q) || c.phone.lowercase().contains(q) || c.email.lowercase().contains(q)
                }.take(8)
                matches.forEach { c ->
                    val selected = draft.customerId == c.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateDraft {
                                    it.copy(
                                        customerId = c.id,
                                        customerKind = c.kind,
                                        customerName = c.name,
                                        customerEmail = c.email,
                                        customerPhone = c.phone,
                                        customerAddress = c.address,
                                        segment = if (c.kind == "Business" && it.segment == "residential") "commercial" else it.segment
                                    )
                                }
                            }
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${c.name} (${c.kind})", fontWeight = FontWeight.Bold)
                            Text(listOf(c.phone, c.email).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            val jc = allJobs.count { it.customerName.equals(c.name, ignoreCase = true) }
                            val ic = allInvoices.count { it.customerName.equals(c.name, ignoreCase = true) }
                            val qc = allQuotes.count { it.customerId == c.id }
                            Text("History: $jc job(s) · $ic invoice(s) · $qc quote(s)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Individual" to "Residential / Individual", "Business" to "Commercial / Business").forEach { (k, lbl) ->
                        FilterChip(
                            selected = draft.customerKind == k,
                            onClick = { onUpdateDraft { it.copy(customerKind = k) } },
                            label = { Text(lbl) }
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.customerName,
                    onValueChange = { v ->
                        onUpdateDraft { it.copy(customerName = v) }
                        onCheckDuplicates(v, draft.customerEmail, draft.customerPhone)
                    },
                    label = { Text("Customer / Business name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wizard_customer_name")
                )
                OutlinedTextField(
                    value = draft.customerPhone,
                    onValueChange = { v ->
                        onUpdateDraft { it.copy(customerPhone = v) }
                        onCheckDuplicates(draft.customerName, draft.customerEmail, v)
                    },
                    label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.customerEmail,
                    onValueChange = { v ->
                        onUpdateDraft { it.copy(customerEmail = v) }
                        onCheckDuplicates(draft.customerName, v, draft.customerPhone)
                    },
                    label = { Text("Email address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.customerAddress,
                    onValueChange = { v -> onUpdateDraft { it.copy(customerAddress = v) } },
                    label = { Text("Primary / Billing address") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (duplicateMatches.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Possible duplicate customer found:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            duplicateMatches.take(3).forEach { dup ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${dup.name} · ${dup.phone.ifBlank { dup.email }}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = {
                                        creatingNew = false
                                        onUpdateDraft {
                                            it.copy(
                                                customerId = dup.id,
                                                customerName = dup.name,
                                                customerEmail = dup.email,
                                                customerPhone = dup.phone
                                            )
                                        }
                                    }) { Text("Use Existing") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardStepMovingDetails(
    draft: QuoteWizardDraft,
    today: String,
    onUpdateDraft: ((QuoteWizardDraft) -> QuoteWizardDraft) -> Unit
) {
    var extraDateInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HfSectionCard(title = "Step 2 · Move Type & Dates") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PricingSegment.entries.forEach { seg ->
                    FilterChip(
                        selected = draft.segment == seg.key,
                        onClick = { onUpdateDraft { it.copy(segment = seg.key) } },
                        label = { Text(seg.label) },
                        modifier = Modifier.testTag("wizard_segment_${seg.key}")
                    )
                }
            }
            OutlinedTextField(
                value = draft.preferredDate,
                onValueChange = { v -> onUpdateDraft { it.copy(preferredDate = v) } },
                label = { Text("Preferred primary date (YYYY-MM-DD)") },
                placeholder = { Text(today) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = extraDateInput,
                    onValueChange = { extraDateInput = it },
                    label = { Text("Add extra service date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val trimmed = extraDateInput.trim()
                        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(trimmed)) {
                            onUpdateDraft { cur ->
                                cur.copy(
                                    extraDates = (cur.extraDates + trimmed).distinct(),
                                    serviceDays = cur.serviceDays + QuoteServiceDayDraft(date = trimmed)
                                )
                            }
                            extraDateInput = ""
                        }
                    }
                ) { Text("Add Date") }
            }
            if (draft.extraDates.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    draft.extraDates.forEach { d ->
                        FilterChip(
                            selected = true,
                            onClick = { onUpdateDraft { it.copy(extraDates = it.extraDates - d) } },
                            label = { Text("$d ×") }
                        )
                    }
                }
            }
        }

        HfSectionCard(
            title = "Addresses & Additional Stops",
            trailing = {
                TextButton(onClick = {
                    onUpdateDraft { it.copy(stops = it.stops + QuoteWizardStop()) }
                }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                    Text("Add Stop")
                }
            }
        ) {
            OutlinedTextField(
                value = draft.pickup,
                onValueChange = { v -> onUpdateDraft { it.copy(pickup = v) } },
                label = { Text("Pickup address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wizard_pickup")
            )
            OutlinedTextField(
                value = draft.delivery,
                onValueChange = { v -> onUpdateDraft { it.copy(delivery = v) } },
                label = { Text("Delivery / Destination address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wizard_delivery")
            )
            draft.stops.forEachIndexed { idx, stop ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = stop.address,
                        onValueChange = { v ->
                            onUpdateDraft { cur ->
                                cur.copy(stops = cur.stops.mapIndexed { j, s -> if (j == idx) s.copy(address = v) else s })
                            }
                        },
                        label = { Text("Extra stop ${idx + 1} address") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        onUpdateDraft { cur -> cur.copy(stops = cur.stops.filterIndexed { j, _ -> j != idx }) }
                    }) { Icon(Icons.Rounded.DeleteOutline, "Remove stop") }
                }
            }
        }

        HfSectionCard(title = "Property, Access & Inventory") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.propertyType,
                    onValueChange = { v -> onUpdateDraft { it.copy(propertyType = v) } },
                    label = { Text("Property type") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.bedrooms,
                    onValueChange = { v -> onUpdateDraft { it.copy(bedrooms = v) } },
                    label = { Text("Bedrooms / Size") },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = draft.stairsAndLifts,
                onValueChange = { v -> onUpdateDraft { it.copy(stairsAndLifts = v) } },
                label = { Text("Stairs & Lifts access") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.parkingAndAccess,
                onValueChange = { v -> onUpdateDraft { it.copy(parkingAndAccess = v) } },
                label = { Text("Parking, truck clearance & walking distance") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.inventorySummary,
                onValueChange = { v -> onUpdateDraft { it.copy(inventorySummary = v) } },
                label = { Text("Key inventory summary") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.heavyItemsSummary,
                onValueChange = { v -> onUpdateDraft { it.copy(heavyItemsSummary = v) } },
                label = { Text("Heavy or fragile items (pianos, safes, IT racks)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.specialInstructions,
                onValueChange = { v -> onUpdateDraft { it.copy(specialInstructions = v) } },
                label = { Text("Special instructions") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardStepTruckAndCrew(
    draft: QuoteWizardDraft,
    today: String,
    rateCards: List<RateCard>,
    onUpdateDraft: ((QuoteWizardDraft) -> QuoteWizardDraft) -> Unit
) {
    val segmentCards = remember(rateCards, draft.segment) {
        rateCards.filter { it.segment == draft.segment && it.effectiveTo == null && it.active }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HfSectionCard(title = "Step 3 · Pricing Mode") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.pricingMode == "hourly_estimate",
                    onClick = { onUpdateDraft { it.copy(pricingMode = "hourly_estimate") } },
                    label = { Text("Hourly / Half-hour Estimate") }
                )
                FilterChip(
                    selected = draft.pricingMode == "fixed_price",
                    onClick = { onUpdateDraft { it.copy(pricingMode = "fixed_price") } },
                    label = { Text("Agreed Fixed Price") }
                )
            }
            if (draft.pricingMode == "fixed_price") {
                OutlinedTextField(
                    value = draft.fixedPrice,
                    onValueChange = { v -> onUpdateDraft { it.copy(fixedPrice = v) } },
                    label = { Text("Agreed fixed labour total (AUD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HfSectionCard(
            title = "Service Days, Trucks & Crew (${draft.serviceDays.size})",
            trailing = {
                TextButton(onClick = {
                    val defaultDate = draft.preferredDate.ifBlank { today }
                    onUpdateDraft { cur ->
                        cur.copy(serviceDays = cur.serviceDays + QuoteServiceDayDraft(date = defaultDate, expanded = true))
                    }
                }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                    Text("Add Day / Truck")
                }
            }
        ) {
            if (segmentCards.isEmpty()) {
                Text(
                    text = "No active rate cards found for ${draft.segment}. Enter an agreed manual rate with a reason below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            draft.serviceDays.forEachIndexed { idx, day ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateDraft { cur ->
                                        cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(expanded = !d.expanded) else d })
                                    }
                                }
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Service Day #${idx + 1} · ${day.date.ifBlank { "Date TBC" }}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${day.truckClass.ifBlank { "Select package" }} · ${day.movers} mover(s) · ${day.estimatedHours}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (draft.serviceDays.size > 1) {
                                IconButton(onClick = {
                                    onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.filterIndexed { j, _ -> j != idx }) }
                                }) { Icon(Icons.Rounded.DeleteOutline, "Remove day") }
                            }
                            Icon(if (day.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        }

                        AnimatedVisibility(visible = day.expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = day.date,
                                    onValueChange = { v ->
                                        onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(date = v) else d }) }
                                    },
                                    label = { Text("Service date (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Rate card / Crew package (${draft.segment})", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    segmentCards.forEach { card ->
                                        val half = PricingAdminLogic.formatHalfHourRate(card.hourlyRate)
                                        FilterChip(
                                            selected = day.rateCardId == card.id,
                                            onClick = {
                                                onUpdateDraft { cur ->
                                                    cur.copy(
                                                        serviceDays = cur.serviceDays.mapIndexed { j, d ->
                                                            if (j == idx) d.copy(
                                                                rateCardId = card.id,
                                                                truckClass = card.name,
                                                                movers = card.movers,
                                                                manualRate = ""
                                                            ) else d
                                                        }
                                                    )
                                                }
                                            },
                                            label = { Text("${card.name} (${half?.let { "$$it/30m" } ?: "$${card.hourlyRate}/hr"})") }
                                        )
                                    }
                                    FilterChip(
                                        selected = day.rateCardId == null,
                                        onClick = {
                                            onUpdateDraft { cur ->
                                                cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(rateCardId = null) else d })
                                            }
                                        },
                                        label = { Text("Agreed manual rate") }
                                    )
                                }

                                if (day.rateCardId == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = day.manualRate,
                                            onValueChange = { v ->
                                                onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(manualRate = v) else d }) }
                                            },
                                            label = { Text("Agreed rate (AUD)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = day.truckClass,
                                            onValueChange = { v ->
                                                onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(truckClass = v) else d }) }
                                            },
                                            label = { Text("Truck label") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = day.manualRateReason,
                                        onValueChange = { v ->
                                            onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(manualRateReason = v) else d }) }
                                        },
                                        label = { Text("Reason for agreed manual rate *") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("half_hour" to "30-min units", "hourly" to "Hourly", "per_day" to "Per day").forEach { (k, lbl) ->
                                        FilterChip(
                                            selected = day.rateUnit == k,
                                            onClick = {
                                                onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(rateUnit = k) else d }) }
                                            },
                                            label = { Text(lbl) }
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = day.movers.toString(),
                                        onValueChange = { v ->
                                            val m = v.toIntOrNull()?.coerceIn(1, 20) ?: day.movers
                                            onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(movers = m) else d }) }
                                        },
                                        label = { Text("Movers") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (day.rateUnit != "per_day") {
                                        OutlinedTextField(
                                            value = day.estimatedHours,
                                            onValueChange = { v ->
                                                onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(estimatedHours = v) else d }) }
                                            },
                                            label = { Text("Est. hours") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = day.callOut,
                                            onCheckedChange = { c ->
                                                onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(callOut = c) else d }) }
                                            }
                                        )
                                        Text("Call-out fee")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = day.travel,
                                            onCheckedChange = { c ->
                                                onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(travel = c) else d }) }
                                            }
                                        )
                                        Text("Travel fee")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = day.publicHoliday,
                                            onCheckedChange = { c ->
                                                onUpdateDraft { cur -> cur.copy(serviceDays = cur.serviceDays.mapIndexed { j, d -> if (j == idx) d.copy(publicHoliday = c) else d }) }
                                            }
                                        )
                                        Text("Public holiday")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardStepServices(
    draft: QuoteWizardDraft,
    catalogue: List<ServiceCatalogueItem>,
    onUpdateDraft: ((QuoteWizardDraft) -> QuoteWizardDraft) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val matchingCatalogue = remember(catalogue, draft.segment, search) {
        val q = search.trim().lowercase()
        catalogue.filter {
            it.effectiveTo == null &&
                it.active &&
                (it.segment == null || it.segment == draft.segment) &&
                (q.isBlank() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HfSectionCard(title = "Step 4 · Additional Services Catalogue") {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search services (monitors, pool tables, boxes…)") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            matchingCatalogue.take(12).forEach { svc ->
                val pStatus = PricingAdminLogic.servicePricingStatus(svc.rate, svc.gstTreatment)
                val existing = draft.selectedServices.firstOrNull { it.catalogueId == svc.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(svc.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when (pStatus) {
                                ServicePricingStatus.UNCONFIGURED -> "Unconfigured — owner must set price first"
                                ServicePricingStatus.ZERO -> "$0.00 approved · ${svc.unit.label}"
                                ServicePricingStatus.PRICED -> "$${svc.rate} · ${svc.unit.label} (${svc.gstTreatment?.label.orEmpty()})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pStatus == ServicePricingStatus.UNCONFIGURED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (existing != null) {
                        OutlinedTextField(
                            value = existing.quantity,
                            onValueChange = { qty ->
                                onUpdateDraft { cur ->
                                    cur.copy(selectedServices = cur.selectedServices.map { s -> if (s.catalogueId == svc.id) s.copy(quantity = qty) else s })
                                }
                            },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.size(width = 76.dp, height = 56.dp)
                        )
                        IconButton(onClick = {
                            onUpdateDraft { cur -> cur.copy(selectedServices = cur.selectedServices.filterNot { it.catalogueId == svc.id }) }
                        }) { Icon(Icons.Rounded.DeleteOutline, "Remove") }
                    } else {
                        OutlinedButton(
                            enabled = pStatus != ServicePricingStatus.UNCONFIGURED,
                            onClick = {
                                onUpdateDraft { cur ->
                                    cur.copy(
                                        selectedServices = cur.selectedServices + QuoteSelectedServiceDraft(
                                            catalogueId = svc.id,
                                            name = svc.name,
                                            unit = svc.unit,
                                            quantity = "1"
                                        )
                                    )
                                }
                            }
                        ) { Text("Add") }
                    }
                }
            }
        }

        HfSectionCard(title = "Packing, Materials, Discount & Deposit") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.packingQuantity,
                    onValueChange = { v -> onUpdateDraft { it.copy(packingQuantity = v) } },
                    label = { Text("Packing units") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.heavyItemCount,
                    onValueChange = { v -> onUpdateDraft { it.copy(heavyItemCount = v) } },
                    label = { Text("Heavy items count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = draft.materialsNotes,
                onValueChange = { v -> onUpdateDraft { it.copy(materialsNotes = v) } },
                label = { Text("Boxes / protective wrapping / materials notes") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = draft.discountEnabled,
                    onCheckedChange = { c -> onUpdateDraft { it.copy(discountEnabled = c) } }
                )
                Text("Apply discount (reason required)")
            }
            if (draft.discountEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.discountType == "percent", onClick = { onUpdateDraft { it.copy(discountType = "percent") } }, label = { Text("% Percent") })
                    FilterChip(selected = draft.discountType == "amount", onClick = { onUpdateDraft { it.copy(discountType = "amount") } }, label = { Text("$ Amount") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.discountValue,
                        onValueChange = { v -> onUpdateDraft { it.copy(discountValue = v) } },
                        label = { Text("Discount value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.discountReason,
                        onValueChange = { v -> onUpdateDraft { it.copy(discountReason = v) } },
                        label = { Text("Discount reason *") },
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
            OutlinedTextField(
                value = draft.depositReceived,
                onValueChange = { v -> onUpdateDraft { it.copy(depositReceived = v) } },
                label = { Text("Deposit actually received (AUD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardStepReviewAndIssue(
    draft: QuoteWizardDraft,
    priceResult: QuotePriceResult?,
    approvedTerms: List<TermsVersionRecord>,
    pricingBusy: Boolean,
    loadingModule: Boolean,
    onUpdateDraft: ((QuoteWizardDraft) -> QuoteWizardDraft) -> Unit,
    onRefreshServerPrice: () -> Unit,
    onSaveDraft: () -> Unit,
    onPreviewPdf: (String) -> Unit,
    onIssueQuotation: (String) -> Unit
) {
    var issueReason by remember { mutableStateOf(draft.revisionReason) }
    val selectedTerms = approvedTerms.firstOrNull { it.id == draft.termsVersionId } ?: approvedTerms.firstOrNull()
    val hasApprovedTerms = selectedTerms != null
    val canIssue = !pricingBusy && !loadingModule && priceResult?.issuable == true && hasApprovedTerms
    val allBlockers = buildList {
        priceResult?.issues?.forEach { add(it) }
        if (!hasApprovedTerms) add("Approved Terms & Conditions are required before issuing.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (allBlockers.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.testTag("wizard_blocker_card")
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Quotation cannot be issued yet:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    allBlockers.forEach { b ->
                        Text("• $b", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        HfSectionCard(
            title = "Step 5 · Server-Calculated Pricing Breakdown",
            trailing = {
                TextButton(onClick = onRefreshServerPrice, enabled = !pricingBusy) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Text("Recalculate")
                }
            }
        ) {
            if (pricingBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Text("Fetching authoritative calculation from server…")
                }
            } else if (priceResult == null) {
                Text("Tap Recalculate to compute server-verified quote pricing.", style = MaterialTheme.typography.bodySmall)
            } else {
                StatusBadge(
                    label = if (priceResult.isEstimate) "Hourly Estimate" else "Guaranteed Fixed Price",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                priceResult.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(line.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${line.quantity} ${line.unit} × $${line.rate} (${line.gst})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("$${line.amount}", fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                SummaryMoneyRow("Subtotal (ex GST)", "$${priceResult.subtotal}")
                if (priceResult.discount != "0.00") SummaryMoneyRow("Discount applied", "-$${priceResult.discount}")
                SummaryMoneyRow("GST", "$${priceResult.gst}")
                SummaryMoneyRow(if (priceResult.isEstimate) "Estimated Total" else "Fixed Total", "$${priceResult.total}", bold = true)
                SummaryMoneyRow("Deposit requested", priceResult.depositRequested?.let { "$$it" } ?: "Not configured")
                SummaryMoneyRow("Deposit received", "$${priceResult.depositReceived}")
                SummaryMoneyRow("Remaining balance", "$${priceResult.balanceEstimated}", bold = true)
                SummaryMoneyRow("Expires", priceResult.validUntil ?: "Not configured")
            }
        }

        HfSectionCard(title = "Terms & Conditions") {
            if (approvedTerms.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        "No owner-approved Terms & Conditions exist for ${draft.segment}. Issuing is blocked until terms are approved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    approvedTerms.forEach { t ->
                        val picked = selectedTerms?.id == t.id
                        FilterChip(
                            selected = picked,
                            onClick = { onUpdateDraft { it.copy(termsVersionId = t.id) } },
                            label = { Text("v${t.versionNumber} · ${t.title}") }
                        )
                    }
                }
                selectedTerms?.let { t ->
                    Text(t.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> onUpdateDraft { it.copy(notes = v) } },
                label = { Text("Quotation notes") },
                modifier = Modifier.fillMaxWidth()
            )
            if (draft.editingQuoteId != null) {
                OutlinedTextField(
                    value = issueReason,
                    onValueChange = { issueReason = it },
                    label = { Text("Revision reason (required when updating an issued quote)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSaveDraft, enabled = !loadingModule) {
                Text("Save Draft")
            }
            draft.editingQuoteId?.let { qid ->
                OutlinedButton(onClick = { onPreviewPdf(qid) }) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Preview PDF")
                }
            }
            Button(
                onClick = { onIssueQuotation(issueReason) },
                enabled = canIssue,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                modifier = Modifier.testTag("wizard_issue_btn")
            ) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Issue Quotation")
            }
        }
    }
}

@Composable
private fun SummaryMoneyRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}
