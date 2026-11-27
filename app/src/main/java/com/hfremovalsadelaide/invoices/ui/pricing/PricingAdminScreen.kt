package com.hfremovalsadelaide.invoices.ui.pricing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.hfremovalsadelaide.invoices.data.GstTreatment
import com.hfremovalsadelaide.invoices.data.PolicyCategory
import com.hfremovalsadelaide.invoices.data.PolicyValueKind
import com.hfremovalsadelaide.invoices.data.PricingAdminLogic
import com.hfremovalsadelaide.invoices.data.PricingPolicyKey
import com.hfremovalsadelaide.invoices.data.PricingPolicyRecord
import com.hfremovalsadelaide.invoices.data.PricingSegment
import com.hfremovalsadelaide.invoices.data.RateCard
import com.hfremovalsadelaide.invoices.data.ServiceCatalogueItem
import com.hfremovalsadelaide.invoices.data.ServicePricingStatus
import com.hfremovalsadelaide.invoices.data.ServiceUnit
import com.hfremovalsadelaide.invoices.data.TermsVersionRecord
import com.hfremovalsadelaide.invoices.ui.PlatformUiState
import com.hfremovalsadelaide.invoices.ui.PlatformViewModel
import com.hfremovalsadelaide.invoices.ui.RoleUiPolicy
import com.hfremovalsadelaide.invoices.ui.components.EmptyStateCard
import com.hfremovalsadelaide.invoices.ui.components.HfSectionCard
import com.hfremovalsadelaide.invoices.ui.components.SkeletonListPlaceholder
import com.hfremovalsadelaide.invoices.ui.components.StatusBadge
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import java.time.LocalDate

enum class PricingTab(val label: String) {
    POLICIES("Policies"),
    RATE_CARDS("Rate Cards"),
    CATALOGUE("Services"),
    TERMS("Terms")
}

@Composable
fun PricingAdminScreen(
    state: PlatformUiState,
    platformViewModel: PlatformViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.user?.id) {
        if (!state.pricingLoaded) platformViewModel.loadPricingAdmin()
    }
    val today = remember { LocalDate.now().toString() }
    val isOwner = RoleUiPolicy.canApprovePricing(state.user)

    var selectedTab by remember { mutableStateOf(PricingTab.POLICIES) }
    var selectedSegment by remember { mutableStateOf(PricingSegment.RESIDENTIAL) }

    PricingAdminContent(
        state = state,
        isOwner = isOwner,
        today = today,
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        selectedSegment = selectedSegment,
        onSelectSegment = { selectedSegment = it },
        onRefresh = { platformViewModel.loadPricingAdmin() },
        onSavePolicyDraft = { segment, key, value, effectiveFrom, notes ->
            platformViewModel.savePolicyDraft(segment, key, value, effectiveFrom, notes)
        },
        onApprovePolicy = { policyId -> platformViewModel.approvePolicy(policyId) },
        onSaveRateCard = { id, name, segment, truckClass, movers, hourlyRate, callOutHours, callOutFee, overtimeRate, effectiveFrom, active, notes ->
            platformViewModel.saveRateCard(
                id, name, segment, truckClass, movers, hourlyRate,
                callOutHours, callOutFee, overtimeRate, effectiveFrom, active, notes
            )
        },
        onSaveService = { id, name, desc, segment, unit, rate, gst, effectiveFrom, active ->
            platformViewModel.saveServiceItem(id, name, desc, segment, unit, rate, gst, effectiveFrom, active)
        },
        onSaveTermsDraft = { segment, title, body, effectiveFrom, supersedesId ->
            platformViewModel.saveTermsDraft(segment, title, body, effectiveFrom, supersedesId)
        },
        onApproveTerms = { termsId -> platformViewModel.approveTermsVersion(termsId) },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PricingAdminContent(
    state: PlatformUiState,
    isOwner: Boolean,
    today: String,
    selectedTab: PricingTab,
    onSelectTab: (PricingTab) -> Unit,
    selectedSegment: PricingSegment,
    onSelectSegment: (PricingSegment) -> Unit,
    onRefresh: () -> Unit,
    onSavePolicyDraft: (String, String, String, String, String) -> Unit,
    onApprovePolicy: (String) -> Unit,
    onSaveRateCard: (String?, String, String, String, Int, String, String, String, String?, String, Boolean, String) -> Unit,
    onSaveService: (String?, String, String, String?, ServiceUnit, String?, GstTreatment?, String, Boolean) -> Unit,
    onSaveTermsDraft: (String?, String, String, String, String?) -> Unit = { _, _, _, _, _ -> },
    onApproveTerms: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editingPolicy by remember { mutableStateOf<Pair<PricingPolicyKey, PricingPolicyRecord?>?>(null) }
    var editingRateCard by remember { mutableStateOf<Pair<Boolean, RateCard?>?>(null) } // Pair(isNew, existingOrNull)
    var editingService by remember { mutableStateOf<Pair<Boolean, ServiceCatalogueItem?>?>(null) }
    var showTermsDialog by remember { mutableStateOf(false) }

    var showPolicyHistory by remember { mutableStateOf(false) }
    var showRateCardHistory by remember { mutableStateOf(false) }

    var serviceQuery by remember { mutableStateOf("") }
    var showExtraServiceFilters by remember { mutableStateOf(false) }
    var serviceScopeFilter by remember { mutableStateOf<String?>(null) }
    var serviceUnitFilter by remember { mutableStateOf<ServiceUnit?>(null) }
    var serviceStatusFilter by remember { mutableStateOf<ServicePricingStatus?>(null) }
    var includeServiceHistory by remember { mutableStateOf(false) }

    val partitioned = remember(state.pricingPolicies, selectedSegment, today) {
        PricingAdminLogic.partitionPolicies(state.pricingPolicies, selectedSegment.key, today)
    }
    val segmentRateCards = remember(state.rateCards, selectedSegment, showRateCardHistory) {
        state.rateCards.filter { card ->
            card.segment == selectedSegment.key && (showRateCardHistory || card.effectiveTo == null)
        }
    }
    val filteredServices = remember(
        state.serviceCatalogue,
        serviceQuery,
        serviceScopeFilter,
        serviceUnitFilter,
        serviceStatusFilter,
        includeServiceHistory
    ) {
        PricingAdminLogic.filterServices(
            services = state.serviceCatalogue,
            query = serviceQuery,
            segmentFilter = serviceScopeFilter,
            unitFilter = serviceUnitFilter,
            statusFilter = serviceStatusFilter,
            includeHistorical = includeServiceHistory
        )
    }

    if (state.loadingModule && !state.pricingLoaded) {
        SkeletonListPlaceholder(modifier = modifier.testTag("pricing_loading"))
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("pricing_admin_screen")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isOwner) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isOwner) MaterialTheme.colorScheme.primary else GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (isOwner) "Owner Approval Access" else "Administrator Drafting Access",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isOwner) {
                                    "You can draft, approve & activate pricing policies, revise rate cards and set service prices."
                                } else {
                                    "You can draft policy proposals. Only an authenticated owner can approve policies or change live prices."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh pricing configuration")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PricingTab.entries.forEach { tab ->
                            val badgeCount = when (tab) {
                                PricingTab.POLICIES -> state.pricingPolicies.count { it.status == "draft" }
                                PricingTab.RATE_CARDS -> state.rateCards.count { it.effectiveTo == null && it.active }
                                PricingTab.CATALOGUE -> state.serviceCatalogue.count { it.effectiveTo == null }
                                PricingTab.TERMS -> state.termsVersions.count { it.status == "approved" && it.effectiveTo == null }
                            }
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                label = { Text("${tab.label} ($badgeCount)", maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreen,
                                    selectedLabelColor = PureWhite
                                ),
                                modifier = Modifier.testTag("pricing_tab_${tab.name.lowercase()}")
                            )
                        }
                    }

                    if (selectedTab != PricingTab.CATALOGUE) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PricingSegment.entries.forEach { seg ->
                                val pendingInSeg = state.pricingPolicies.count { it.segment == seg.key && it.status == "draft" }
                                val label = if (pendingInSeg > 0 && selectedTab == PricingTab.POLICIES) {
                                    "${seg.label} · $pendingInSeg pending"
                                } else {
                                    seg.label
                                }
                                FilterChip(
                                    selected = selectedSegment == seg,
                                    onClick = { onSelectSegment(seg) },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.testTag("pricing_segment_${seg.key}")
                                )
                            }
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            PricingTab.POLICIES -> {
                if (partitioned.pendingDrafts.isNotEmpty()) {
                    item {
                        HfSectionCard(
                            title = "Pending Approvals (${partitioned.pendingDrafts.size})",
                            trailing = {
                                StatusBadge(
                                    label = "Awaiting Owner",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        ) {
                            partitioned.pendingDrafts.forEach { draft ->
                                val meta = PricingPolicyKey.fromKey(draft.key)
                                PolicyVersionRow(
                                    title = meta?.label ?: draft.key,
                                    valueText = formatPolicyValue(meta, draft.value),
                                    metaText = "Effective ${draft.effectiveFrom}" + if (draft.notes.isNotBlank()) " · ${draft.notes}" else "",
                                    statusLabel = "Draft",
                                    statusContainer = MaterialTheme.colorScheme.secondaryContainer,
                                    statusContent = MaterialTheme.colorScheme.onSecondaryContainer,
                                    actionContent = {
                                        if (isOwner) {
                                            Button(
                                                onClick = { onApprovePolicy(draft.id) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ForestGreen,
                                                    contentColor = PureWhite
                                                ),
                                                modifier = Modifier.testTag("approve_policy_${draft.key}")
                                            ) {
                                                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                                                Spacer(Modifier.size(6.dp))
                                                Text("Approve & Activate")
                                            }
                                        } else {
                                            Text(
                                                text = "Owner approval required to activate.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                PolicyCategory.entries.forEach { category ->
                    val keysInCategory = PricingPolicyKey.entries.filter { it.category == category }
                    item(key = "cat_${category.name}") {
                        HfSectionCard(title = category.label) {
                            keysInCategory.forEachIndexed { idx, policyKey ->
                                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val active = partitioned.activeByKey[policyKey.key]
                                val pendingCount = partitioned.pendingDrafts.count { it.key == policyKey.key }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("policy_row_${policyKey.key}")
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = policyKey.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = policyKey.hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (active != null) {
                                                StatusBadge(
                                                    label = formatPolicyValue(policyKey, active.value),
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "Since ${active.effectiveFrom}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.align(Alignment.CenterVertically)
                                                )
                                            } else {
                                                StatusBadge(
                                                    label = "Unconfigured",
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (pendingCount > 0) {
                                                StatusBadge(
                                                    label = "$pendingCount draft",
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { editingPolicy = policyKey to active },
                                        modifier = Modifier.testTag("edit_policy_${policyKey.key}")
                                    ) {
                                        Text("Propose")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    HfSectionCard(
                        title = "Policy Version History (${partitioned.historicalAndFuture.size})",
                        trailing = {
                            TextButton(onClick = { showPolicyHistory = !showPolicyHistory }) {
                                Icon(Icons.Rounded.History, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(if (showPolicyHistory) "Hide" else "Show")
                            }
                        }
                    ) {
                        if (!showPolicyHistory) {
                            Text(
                                text = "Historical and future-scheduled approved versions are preserved immutably.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (partitioned.historicalAndFuture.isEmpty()) {
                            Text(
                                text = "No superseded or future-dated policy versions in ${selectedSegment.label}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            partitioned.historicalAndFuture.forEach { rec ->
                                val meta = PricingPolicyKey.fromKey(rec.key)
                                val isFuture = rec.effectiveFrom > today
                                PolicyVersionRow(
                                    title = meta?.label ?: rec.key,
                                    valueText = formatPolicyValue(meta, rec.value),
                                    metaText = "Effective ${rec.effectiveFrom}" + if (rec.notes.isNotBlank()) " · ${rec.notes}" else "",
                                    statusLabel = if (isFuture) "Scheduled" else "Superseded",
                                    statusContainer = MaterialTheme.colorScheme.surfaceVariant,
                                    statusContent = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            PricingTab.RATE_CARDS -> {
                if (selectedSegment == PricingSegment.RESIDENTIAL) {
                    item {
                        HfSectionCard(
                            title = "Verified Residential Rate Benchmarks",
                            trailing = {
                                StatusBadge(
                                    label = "30-min / Hourly",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PricingAdminLogic.verifiedResidentialBenchmarks.forEach { b ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.testTag("verified_benchmark_${b.halfHourRate}")
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(b.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                text = "$${b.halfHourRate} / 30 min · $${b.hourlyRate} / hr",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = showRateCardHistory,
                            onClick = { showRateCardHistory = !showRateCardHistory },
                            label = { Text("Show superseded versions") },
                            leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) }
                        )
                        Spacer(Modifier.weight(1f))
                        if (isOwner) {
                            Button(
                                onClick = { editingRateCard = true to null },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                                modifier = Modifier.testTag("add_rate_card_btn")
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("New Rate Card")
                            }
                        }
                    }
                }

                if (segmentRateCards.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.LocalShipping,
                            title = "No ${selectedSegment.label} rate cards",
                            message = if (isOwner) {
                                "Create an owner-approved truck & crew rate card for ${selectedSegment.label} quotations."
                            } else {
                                "No rate cards configured yet for ${selectedSegment.label}. Only owners can add rate cards."
                            }
                        )
                    }
                } else {
                    items(segmentRateCards, key = { it.id }) { card ->
                        RateCardItemCard(
                            card = card,
                            isOwner = isOwner,
                            onRevise = { editingRateCard = false to card }
                        )
                    }
                }
            }

            PricingTab.CATALOGUE -> {
                item {
                    HfSectionCard(
                        title = "Additional Service Catalogue",
                        trailing = {
                            Button(
                                onClick = { editingService = true to null },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                                modifier = Modifier.testTag("add_service_btn")
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Custom Service")
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = serviceQuery,
                            onValueChange = { serviceQuery = it },
                            label = { Text("Search services (monitors, pool tables, boxes…)") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("service_search_input")
                        )

                        val unconfiguredActiveCount = state.serviceCatalogue.count {
                            it.effectiveTo == null &&
                                PricingAdminLogic.servicePricingStatus(it.rate, it.gstTreatment) == ServicePricingStatus.UNCONFIGURED
                        }
                        if (unconfiguredActiveCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.WarningAmber, null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "$unconfiguredActiveCount catalogue item(s) have unconfigured prices and cannot be billed until an owner sets a rate & GST.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = serviceStatusFilter == ServicePricingStatus.UNCONFIGURED,
                                onClick = {
                                    serviceStatusFilter =
                                        if (serviceStatusFilter == ServicePricingStatus.UNCONFIGURED) null else ServicePricingStatus.UNCONFIGURED
                                },
                                label = { Text("Unconfigured") },
                                modifier = Modifier.testTag("filter_unconfigured")
                            )
                            FilterChip(
                                selected = serviceStatusFilter == ServicePricingStatus.ZERO,
                                onClick = {
                                    serviceStatusFilter =
                                        if (serviceStatusFilter == ServicePricingStatus.ZERO) null else ServicePricingStatus.ZERO
                                },
                                label = { Text("Approved $0.00") },
                                modifier = Modifier.testTag("filter_zero")
                            )
                            FilterChip(
                                selected = serviceStatusFilter == ServicePricingStatus.PRICED,
                                onClick = {
                                    serviceStatusFilter =
                                        if (serviceStatusFilter == ServicePricingStatus.PRICED) null else ServicePricingStatus.PRICED
                                },
                                label = { Text("Priced") },
                                modifier = Modifier.testTag("filter_priced")
                            )
                            val activeExtraCount = listOfNotNull(serviceScopeFilter, serviceUnitFilter).size +
                                if (includeServiceHistory) 1 else 0
                            FilterChip(
                                selected = showExtraServiceFilters || activeExtraCount > 0,
                                onClick = { showExtraServiceFilters = !showExtraServiceFilters },
                                label = {
                                    Text(if (activeExtraCount > 0) "Type & unit ($activeExtraCount)" else "Type & unit")
                                },
                                modifier = Modifier.testTag("toggle_extra_service_filters")
                            )
                        }

                        if (showExtraServiceFilters) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val scopeOptions: List<Pair<String?, String>> = listOf(
                                    null to "All move types",
                                    "shared" to "Shared"
                                ) + PricingSegment.entries.map { it.key to it.label }
                                scopeOptions.forEach { (key, label) ->
                                    FilterChip(
                                        selected = serviceScopeFilter == key,
                                        onClick = { serviceScopeFilter = key },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = serviceUnitFilter == null,
                                    onClick = { serviceUnitFilter = null },
                                    label = { Text("All units") }
                                )
                                ServiceUnit.entries.forEach { unit ->
                                    FilterChip(
                                        selected = serviceUnitFilter == unit,
                                        onClick = { serviceUnitFilter = if (serviceUnitFilter == unit) null else unit },
                                        label = { Text(unit.label) }
                                    )
                                }
                                FilterChip(
                                    selected = includeServiceHistory,
                                    onClick = { includeServiceHistory = !includeServiceHistory },
                                    label = { Text("History") },
                                    leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }

                if (filteredServices.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Inventory2,
                            title = "No matching services",
                            message = "Adjust your search or filters, or add a custom additional service category."
                        )
                    }
                } else {
                    items(filteredServices, key = { it.id }) { svc ->
                        ServiceCatalogueCard(
                            item = svc,
                            isOwner = isOwner,
                            onEdit = { editingService = false to svc }
                        )
                    }
                }
            }

            PricingTab.TERMS -> {
                val segTerms = state.termsVersions.filter { it.segment == null || it.segment == selectedSegment.key }
                item {
                    HfSectionCard(
                        title = "Versioned Terms & Conditions",
                        trailing = {
                            Button(
                                onClick = { showTermsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                                modifier = Modifier.testTag("add_terms_btn")
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("New Draft")
                            }
                        }
                    ) {
                        Text(
                            text = "Issued quotations require an active owner-approved Terms & Conditions version.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (segTerms.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Policy,
                            title = "No Terms & Conditions yet",
                            message = "Draft Terms & Conditions and have an owner approve them before issuing quotations."
                        )
                    }
                } else {
                    items(segTerms, key = { it.id }) { terms ->
                        TermsVersionCard(
                            terms = terms,
                            isOwner = isOwner,
                            onApprove = { onApproveTerms(terms.id) }
                        )
                    }
                }
            }
        }
    }

    editingPolicy?.let { (policyKey, existing) ->
        PolicyDraftDialog(
            segment = selectedSegment,
            policyKey = policyKey,
            existing = existing,
            defaultEffectiveFrom = today,
            onDismiss = { editingPolicy = null },
            onSave = { value, effectiveFrom, notes ->
                editingPolicy = null
                onSavePolicyDraft(selectedSegment.key, policyKey.key, value, effectiveFrom, notes)
            }
        )
    }

    editingRateCard?.let { (isNew, existing) ->
        RateCardEditorDialog(
            segment = selectedSegment,
            existing = if (isNew) null else existing,
            defaultEffectiveFrom = today,
            onDismiss = { editingRateCard = null },
            onSave = { name, truckClass, movers, hourlyRate, callOutHours, callOutFee, overtimeRate, effectiveFrom, active, notes ->
                editingRateCard = null
                onSaveRateCard(
                    if (isNew) null else existing?.id,
                    name,
                    selectedSegment.key,
                    truckClass,
                    movers,
                    hourlyRate,
                    callOutHours,
                    callOutFee,
                    overtimeRate,
                    effectiveFrom,
                    active,
                    notes
                )
            }
        )
    }

    editingService?.let { (isNew, existing) ->
        ServiceEditorDialog(
            isNew = isNew,
            existing = existing,
            isOwner = isOwner,
            defaultEffectiveFrom = today,
            onDismiss = { editingService = null },
            onSave = { name, description, segment, unit, rate, gstTreatment, effectiveFrom, active ->
                editingService = null
                onSaveService(
                    if (isNew) null else existing?.id,
                    name,
                    description,
                    segment,
                    unit,
                    rate,
                    gstTreatment,
                    effectiveFrom,
                    active
                )
            }
        )
    }

    if (showTermsDialog) {
        TermsDraftDialog(
            defaultSegment = selectedSegment.key,
            approvedTerms = state.termsVersions.filter { it.status == "approved" && it.effectiveTo == null },
            defaultEffectiveFrom = today,
            onDismiss = { showTermsDialog = false },
            onSave = { segment, title, body, effectiveFrom, supersedesId ->
                showTermsDialog = false
                onSaveTermsDraft(segment, title, body, effectiveFrom, supersedesId)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermsVersionCard(
    terms: TermsVersionRecord,
    isOwner: Boolean,
    onApprove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("terms_card_${terms.id}")
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("v${terms.versionNumber} · ${terms.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusBadge(
                    label = when {
                        terms.status != "approved" -> "Draft"
                        terms.effectiveTo != null -> "Superseded"
                        else -> "Approved"
                    },
                    containerColor = if (terms.status == "approved" && terms.effectiveTo == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (terms.status == "approved" && terms.effectiveTo == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "${PricingSegment.fromKey(terms.segment)?.label ?: "Universal (all segments)"} · Effective ${terms.effectiveFrom}${terms.effectiveTo?.let { " → $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(terms.body, style = MaterialTheme.typography.bodySmall)
            if (terms.status == "draft") {
                if (isOwner) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                        modifier = Modifier.testTag("approve_terms_${terms.versionNumber}")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Approve Terms")
                    }
                } else {
                    Text(
                        "Owner approval required to activate terms.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermsDraftDialog(
    defaultSegment: String,
    approvedTerms: List<TermsVersionRecord>,
    defaultEffectiveFrom: String,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String?) -> Unit
) {
    var segment by remember { mutableStateOf<String?>(defaultSegment) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var effectiveFrom by remember { mutableStateOf(defaultEffectiveFrom) }
    var supersedesId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draft Terms & Conditions", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Scope", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = segment == null, onClick = { segment = null; supersedesId = null }, label = { Text("Universal") })
                    PricingSegment.entries.forEach { s ->
                        FilterChip(selected = segment == s.key, onClick = { segment = s.key; supersedesId = null }, label = { Text(s.label) })
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it; errorMessage = null }, label = { Text("Title *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = effectiveFrom, onValueChange = { effectiveFrom = it }, label = { Text("Effective from (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val supersedingChoices = approvedTerms.filter { it.segment == segment }
                if (supersedingChoices.isNotEmpty()) {
                    Text("Supersede active version (optional)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = supersedesId == null, onClick = { supersedesId = null }, label = { Text("None") })
                        supersedingChoices.forEach { t ->
                            FilterChip(selected = supersedesId == t.id, onClick = { supersedesId = t.id }, label = { Text("v${t.versionNumber} · ${t.title}") })
                        }
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it; errorMessage = null },
                    label = { Text("Terms & Conditions body *") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        title.trim().length < 2 -> errorMessage = "Enter a title."
                        body.trim().length < 5 -> errorMessage = "Enter terms content."
                        !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(effectiveFrom.trim()) -> errorMessage = "Use YYYY-MM-DD date."
                        else -> onSave(segment, title.trim(), body.trim(), effectiveFrom.trim(), supersedesId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
            ) { Text("Save Draft") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PolicyVersionRow(
    title: String,
    valueText: String,
    metaText: String,
    statusLabel: String,
    statusContainer: androidx.compose.ui.graphics.Color,
    statusContent: androidx.compose.ui.graphics.Color,
    actionContent: (@Composable () -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusBadge(label = statusLabel, containerColor = statusContainer, contentColor = statusContent)
            }
            Text(valueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(metaText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actionContent?.invoke()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateCardItemCard(
    card: RateCard,
    isOwner: Boolean,
    onRevise: () -> Unit
) {
    val halfHour = PricingAdminLogic.formatHalfHourRate(card.hourlyRate)
    val isHistorical = card.effectiveTo != null
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rate_card_${card.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${card.truckClass} · ${card.movers} mover(s) · Effective ${card.effectiveFrom}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isOwner && !isHistorical) {
                    IconButton(onClick = onRevise, modifier = Modifier.testTag("revise_rate_card_${card.id}")) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit or version rate card")
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadge(
                    label = "$${card.hourlyRate}/hr" + (halfHour?.let { " ($${it} / 30 min)" } ?: ""),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatusBadge(
                    label = when {
                        isHistorical -> "Superseded ${card.effectiveTo}"
                        card.active -> "Active"
                        else -> "Inactive"
                    },
                    containerColor = if (card.active && !isHistorical) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (card.active && !isHistorical) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (card.supersedesId != null) {
                    StatusBadge(
                        label = "Versioned",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = buildString {
                    append("Call-out: ${card.callOutHours}h / $${card.callOutFee}")
                    card.overtimeRate?.let { append(" · Overtime: $$it/hr") }
                    if (card.notes.isNotBlank()) append(" · ${card.notes}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceCatalogueCard(
    item: ServiceCatalogueItem,
    isOwner: Boolean,
    onEdit: () -> Unit
) {
    val pricingStatus = PricingAdminLogic.servicePricingStatus(item.rate, item.gstTreatment)
    val isHistorical = item.effectiveTo != null
    val canOpenEditor = isOwner || pricingStatus == ServicePricingStatus.UNCONFIGURED
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_card_${item.name}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (canOpenEditor && !isHistorical) {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_service_${item.name}")) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${item.name}")
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (pricingStatus) {
                    ServicePricingStatus.UNCONFIGURED -> StatusBadge(
                        label = "Unconfigured (no charge set)",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                    ServicePricingStatus.ZERO -> StatusBadge(
                        label = "Approved $0.00 · ${item.gstTreatment?.label.orEmpty()}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    ServicePricingStatus.PRICED -> StatusBadge(
                        label = "$${item.rate} · ${item.gstTreatment?.label.orEmpty()}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                StatusBadge(
                    label = item.unit.label,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(
                    label = PricingSegment.fromKey(item.segment)?.label ?: "All move types",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(
                    label = when {
                        isHistorical -> "Superseded ${item.effectiveTo}"
                        item.active -> "Available"
                        else -> "Disabled"
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Effective ${item.effectiveFrom}" + if (item.supersedesId != null) " · Historical version tracked" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyDraftDialog(
    segment: PricingSegment,
    policyKey: PricingPolicyKey,
    existing: PricingPolicyRecord?,
    defaultEffectiveFrom: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var value by remember {
        mutableStateOf(
            existing?.value ?: policyKey.options.firstOrNull()?.value.orEmpty()
        )
    }
    var effectiveFrom by remember { mutableStateOf(defaultEffectiveFrom) }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Propose ${segment.label}: ${policyKey.label}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${policyKey.hint}. Draft proposals require owner approval before taking effect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (policyKey.kind == PolicyValueKind.BOOLEAN || policyKey.kind == PolicyValueKind.ENUM) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        policyKey.options.forEach { opt ->
                            FilterChip(
                                selected = value == opt.value,
                                onClick = {
                                    value = opt.value
                                    errorMessage = null
                                },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            errorMessage = null
                        },
                        label = {
                            Text(
                                if (policyKey.unitLabel != null) "Proposed value (${policyKey.unitLabel})"
                                else "Proposed value"
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (policyKey.kind == PolicyValueKind.INTEGER) {
                                KeyboardType.Number
                            } else {
                                KeyboardType.Decimal
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("policy_draft_value_input")
                    )
                }
                OutlinedTextField(
                    value = effectiveFrom,
                    onValueChange = {
                        effectiveFrom = it
                        errorMessage = null
                    },
                    label = { Text("Effective from (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Reason / approval notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val valueErr = PricingAdminLogic.validatePolicyValue(policyKey, value)
                    val dateErr = if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(effectiveFrom.trim())) {
                        null
                    } else {
                        "Use YYYY-MM-DD effective date."
                    }
                    val firstErr = valueErr ?: dateErr
                    if (firstErr != null) {
                        errorMessage = firstErr
                    } else {
                        onSave(value.trim(), effectiveFrom.trim(), notes.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                modifier = Modifier.testTag("policy_draft_submit")
            ) {
                Text("Save Draft")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RateCardEditorDialog(
    segment: PricingSegment,
    existing: RateCard?,
    defaultEffectiveFrom: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String, String, String?, String, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var truckClass by remember { mutableStateOf(existing?.truckClass ?: "8t") }
    var movers by remember { mutableStateOf((existing?.movers ?: 2).toString()) }
    var hourlyRate by remember { mutableStateOf(existing?.hourlyRate.orEmpty()) }
    var callOutHours by remember { mutableStateOf(existing?.callOutHours ?: "0") }
    var callOutFee by remember { mutableStateOf(existing?.callOutFee ?: "0") }
    var overtimeRate by remember { mutableStateOf(existing?.overtimeRate.orEmpty()) }
    var effectiveFrom by remember { mutableStateOf(existing?.effectiveFrom ?: defaultEffectiveFrom) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val halfHourPreview = PricingAdminLogic.formatHalfHourRate(hourlyRate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "New ${segment.label} Rate Card" else "Revise Rate Card",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (existing != null) {
                    Text(
                        text = "Editing rate or effective date creates a new immutable rate card version and preserves history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Package name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = truckClass, onValueChange = { truckClass = it }, label = { Text("Truck class") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = movers,
                        onValueChange = { movers = it },
                        label = { Text("Movers") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = hourlyRate,
                    onValueChange = {
                        hourlyRate = it
                        errorMessage = null
                    },
                    label = { Text("Hourly rate (AUD)") },
                    supportingText = halfHourPreview?.let { { Text("30-min increment equivalent: $$it / 30 min") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = callOutHours,
                        onValueChange = { callOutHours = it },
                        label = { Text("Call-out hrs") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = callOutFee,
                        onValueChange = { callOutFee = it },
                        label = { Text("Call-out fee") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = overtimeRate,
                    onValueChange = { overtimeRate = it },
                    label = { Text("Overtime rate AUD/hr (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = effectiveFrom,
                    onValueChange = { effectiveFrom = it },
                    label = { Text("Effective from (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Active for quoting", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Version / pricing notes") }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = movers.trim().toIntOrNull()
                    val hr = hourlyRate.trim().toBigDecimalOrNull()
                    val dateOk = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(effectiveFrom.trim())
                    when {
                        name.trim().isEmpty() -> errorMessage = "Package name is required."
                        truckClass.trim().isEmpty() -> errorMessage = "Truck class is required."
                        m == null || m !in 1..20 -> errorMessage = "Movers must be 1–20."
                        hr == null || hr < java.math.BigDecimal.ZERO -> errorMessage = "Enter a valid hourly rate."
                        !dateOk -> errorMessage = "Use YYYY-MM-DD effective date."
                        else -> onSave(
                            name.trim(),
                            truckClass.trim(),
                            m,
                            hourlyRate.trim(),
                            callOutHours.trim().ifBlank { "0" },
                            callOutFee.trim().ifBlank { "0" },
                            overtimeRate.trim().takeIf { it.isNotEmpty() },
                            effectiveFrom.trim(),
                            active,
                            notes.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
            ) {
                Text("Save Rate Card")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceEditorDialog(
    isNew: Boolean,
    existing: ServiceCatalogueItem?,
    isOwner: Boolean,
    defaultEffectiveFrom: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, ServiceUnit, String?, GstTreatment?, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var segment by remember { mutableStateOf(existing?.segment) }
    var unit by remember { mutableStateOf(existing?.unit ?: ServiceUnit.PER_ITEM) }
    var rate by remember { mutableStateOf(existing?.rate.orEmpty()) }
    var gstTreatment by remember { mutableStateOf(existing?.gstTreatment) }
    var effectiveFrom by remember { mutableStateOf(existing?.effectiveFrom ?: defaultEffectiveFrom) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "New Additional Service" else "Update Service Catalogue Item",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Service name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Move type scope", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = segment == null, onClick = { segment = null }, label = { Text("All move types") })
                    PricingSegment.entries.forEach { seg ->
                        FilterChip(selected = segment == seg.key, onClick = { segment = seg.key }, label = { Text(seg.label) })
                    }
                }
                Text("Charging unit", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ServiceUnit.entries.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) })
                    }
                }
                if (isOwner) {
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = it; errorMessage = null },
                        label = { Text("Approved rate AUD (leave blank = Unconfigured, 0 = Approved $0.00)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("service_rate_input")
                    )
                    Text("GST treatment (required whenever a rate is set)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = gstTreatment == null, onClick = { gstTreatment = null }, label = { Text("Unconfigured") })
                        GstTreatment.entries.forEach { g ->
                            FilterChip(selected = gstTreatment == g, onClick = { gstTreatment = g }, label = { Text(g.label) })
                        }
                    }
                } else {
                    Text(
                        text = "Only an authenticated owner can set or revise customer service prices. You can save unpriced metadata or catalogue descriptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = effectiveFrom,
                    onValueChange = { effectiveFrom = it },
                    label = { Text("Effective from (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Available for quoting", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedRate = rate.trim().takeIf { it.isNotEmpty() }
                    val parsedRate = trimmedRate?.toBigDecimalOrNull()
                    val dateOk = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(effectiveFrom.trim())
                    when {
                        name.trim().isEmpty() -> errorMessage = "Service name is required."
                        !dateOk -> errorMessage = "Use YYYY-MM-DD effective date."
                        isOwner && trimmedRate != null && (parsedRate == null || parsedRate < java.math.BigDecimal.ZERO) ->
                            errorMessage = "Enter a valid non-negative price or leave blank."
                        isOwner && (trimmedRate != null) != (gstTreatment != null) ->
                            errorMessage = "Provide both price and GST treatment, or leave both unconfigured."
                        else -> onSave(
                            name.trim(),
                            description.trim(),
                            segment,
                            unit,
                            if (isOwner) trimmedRate else existing?.rate,
                            if (isOwner) gstTreatment else existing?.gstTreatment,
                            effectiveFrom.trim(),
                            active
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
            ) {
                Text("Save Service")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatPolicyValue(meta: PricingPolicyKey?, rawValue: String): String {
    if (meta == null) return rawValue
    meta.options.firstOrNull { it.value == rawValue }?.let { return it.label }
    return when {
        meta.kind == PolicyValueKind.MONEY -> "$$rawValue"
        meta.unitLabel != null -> "$rawValue ${meta.unitLabel}"
        else -> rawValue
    }
}
