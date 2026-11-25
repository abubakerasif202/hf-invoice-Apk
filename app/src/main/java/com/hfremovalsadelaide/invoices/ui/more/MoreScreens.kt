package com.hfremovalsadelaide.invoices.ui.more

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.offline.QueueOpStatus
import com.hfremovalsadelaide.invoices.ui.MoreSection
import com.hfremovalsadelaide.invoices.ui.PlatformUiState
import com.hfremovalsadelaide.invoices.ui.PlatformViewModel
import com.hfremovalsadelaide.invoices.ui.components.EmptyStateCard
import com.hfremovalsadelaide.invoices.ui.components.HfSectionCard
import com.hfremovalsadelaide.invoices.ui.components.StatusBadge
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import java.time.LocalDate

@Composable
fun MoreRouterScreen(
    section: MoreSection,
    platformState: PlatformUiState,
    platformViewModel: PlatformViewModel,
    onSelectSection: (MoreSection) -> Unit,
    onOpenSettings: () -> Unit,
    onDownloadCsvExport: () -> Unit,
    onDownloadStatementPdf: (statementId: String) -> Unit,
    onOpenJob: (String) -> Unit,
    workspaceContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (section == MoreSection.WORKSPACE) {
        BackHandler { onSelectSection(MoreSection.HUB) }
        workspaceContent()
        return
    }

    if (section != MoreSection.HUB) {
        BackHandler { onSelectSection(MoreSection.HUB) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("more_screen")
    ) {
        val title = when (section) {
            MoreSection.HUB -> "Operations Hub"
            MoreSection.STAFF -> "Staff & Permissions"
            MoreSection.FLEET -> "Vehicles & Fleet"
            MoreSection.TIMESHEETS -> "Timesheets & Pay"
            MoreSection.ANALYTICS -> "Reports & Analytics"
            MoreSection.NOTIFICATIONS -> "Notifications"
            MoreSection.OFFLINE_QUEUE -> "Offline Sync Queue"
            MoreSection.WORKSPACE -> "Workspace"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ForestGreen)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (section != MoreSection.HUB) {
                    IconButton(onClick = { onSelectSection(MoreSection.HUB) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = PureWhite)
                    }
                } else {
                    Spacer(Modifier.size(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = PureWhite, fontWeight = FontWeight.Bold)
                    Text(
                        platformState.user?.let { "${it.name} · ${it.role.displayLabel}" } ?: "HF Removals Adelaide",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAccent
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = PureWhite)
                }
            }
        }

        when (section) {
            MoreSection.HUB -> MoreHubGrid(platformState, onSelectSection, onOpenSettings)
            MoreSection.STAFF -> StaffScreen(platformState, platformViewModel)
            MoreSection.FLEET -> FleetScreen(platformState) { name, rego, vehicleClass, tonnes ->
                platformViewModel.saveVehicle(null, name, rego, vehicleClass, tonnes, "available", null, null, "")
            }
            MoreSection.TIMESHEETS -> TimesheetsScreen(platformState, platformViewModel, onDownloadStatementPdf)
            MoreSection.ANALYTICS -> AnalyticsScreen(platformState, onDownloadCsvExport)
            MoreSection.NOTIFICATIONS -> NotificationsScreen(platformState, platformViewModel, onOpenJob)
            MoreSection.OFFLINE_QUEUE -> OfflineQueueScreen(platformState, platformViewModel)
            MoreSection.WORKSPACE -> Unit
        }
    }
}

@Composable
private fun MoreHubGrid(
    state: PlatformUiState,
    onSelectSection: (MoreSection) -> Unit,
    onOpenSettings: () -> Unit
) {
    val user = state.user
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (user?.can("staff.manage") == true) {
            item {
                MoreRowItem(
                    icon = Icons.Rounded.People,
                    title = "Staff & Accounts",
                    subtitle = "${state.staff.size} team members · Invite, roles & revocation",
                    onClick = { onSelectSection(MoreSection.STAFF) },
                    testTag = "more_staff"
                )
            }
        }
        if (user?.can("fleet.read") == true) {
            item {
                MoreRowItem(
                    icon = Icons.Rounded.LocalShipping,
                    title = "Vehicles & Fleet",
                    subtitle = "${state.fleet?.vehicles?.size ?: 0} trucks · Rego, maintenance & conflicts",
                    onClick = { onSelectSection(MoreSection.FLEET) },
                    testTag = "more_fleet"
                )
            }
        }
        item {
            MoreRowItem(
                icon = Icons.Rounded.Schedule,
                title = "Timesheets & Pay Statements",
                subtitle = "${state.timesheets?.timesheets?.size ?: 0} logs · Employee & subcontractor pay",
                onClick = { onSelectSection(MoreSection.TIMESHEETS) },
                testTag = "more_timesheets"
            )
        }
        if (user?.can("reports.read") == true) {
            item {
                MoreRowItem(
                    icon = Icons.Rounded.Analytics,
                    title = "Reports, GST & CSV Export",
                    subtitle = "Revenue, collections, utilisation & repeat rate",
                    onClick = { onSelectSection(MoreSection.ANALYTICS) },
                    testTag = "more_analytics"
                )
            }
        }
        item {
            MoreRowItem(
                icon = Icons.Rounded.Notifications,
                title = "Notifications Center",
                subtitle = "${state.unreadNotificationsCount} unread alerts",
                onClick = { onSelectSection(MoreSection.NOTIFICATIONS) },
                testTag = "more_notifications"
            )
        }
        item {
            MoreRowItem(
                icon = Icons.Rounded.Sync,
                title = "Offline Sync Queue",
                subtitle = "${state.pendingOfflineCount} pending · ${state.conflictOfflineCount} conflict(s)",
                onClick = { onSelectSection(MoreSection.OFFLINE_QUEUE) },
                testTag = "more_offline_queue"
            )
        }
        item {
            MoreRowItem(
                icon = Icons.Rounded.Language,
                title = "Classic Web Workspace",
                subtitle = "Full web console & Google Calendar connect",
                onClick = { onSelectSection(MoreSection.WORKSPACE) },
                testTag = "more_workspace"
            )
        }
        item {
            MoreRowItem(
                icon = Icons.Rounded.Settings,
                title = "Theme, Privacy & Account",
                subtitle = "Light / Dark theme, FLAG_SECURE privacy mode & sign-out",
                onClick = onOpenSettings,
                testTag = "more_settings"
            )
        }
    }
}

@Composable
private fun MoreRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffScreen(state: PlatformUiState, vm: PlatformViewModel) {
    val canManage = state.user?.can("staff.manage") == true
    var showInviteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (canManage) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { showInviteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Invite Staff Member")
                }
            }
        }
        state.lastInviteOrResetToken?.let { tok ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("One-time invite / reset token (share privately):", fontWeight = FontWeight.Bold)
                    Text(tok, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.clearPreviews() }) { Text("Dismiss") }
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.staff, key = { it.id }) { member ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusBadge(
                                label = if (member.active) member.role else "Revoked",
                                containerColor = if (member.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (member.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text("${member.email} · ${member.phone.ifBlank { "No phone" }}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${member.employmentType} · Rate: ${member.hourlyRate?.let { "$$it/hr" } ?: "Not set"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (canManage) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.resetStaffPassword(member.id) }) { Text("Issue Reset Token") }
                                OutlinedButton(onClick = { vm.setStaffActive(member.id, !member.active) }) {
                                    Text(if (member.active) "Revoke Access" else "Restore Access")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        InviteStaffDialog(
            onDismiss = { showInviteDialog = false },
            onSave = { name, email, phone, role, empType, rate ->
                showInviteDialog = false
                vm.createStaff(name, email, phone, role, empType, rate)
            }
        )
    }
}

@Composable
internal fun FleetScreen(
    state: PlatformUiState,
    onAddVehicle: (name: String, registration: String, vehicleClass: String, tonnes: String?) -> Unit
) {
    val canWrite = state.user?.can("fleet.manage") == true
    val fleet = state.fleet
    var showAddVehicle by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (canWrite) {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = { showAddVehicle = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Add Vehicle")
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (fleet != null && fleet.alerts.isNotEmpty()) {
                item {
                    HfSectionCard(title = "Fleet Alerts") {
                        fleet.alerts.forEach { a ->
                            Text("• ${a.vehicleName}: ${a.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            items(fleet?.vehicles.orEmpty(), key = { it.id }) { v ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${v.name} (${v.registration})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusBadge(v.status, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text("${v.vehicleClass} · ${v.capacityTonnes ?: "-"}t", style = MaterialTheme.typography.bodySmall)
                        Text("Rego: ${v.regoExpiry ?: "-"} · Service due: ${v.nextServiceDate ?: "-"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showAddVehicle) {
        NewVehicleDialog(
            onDismiss = { showAddVehicle = false },
            onSave = { name, rego, cls, tonnes ->
                showAddVehicle = false
                onAddVehicle(name, rego, cls, tonnes)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimesheetsScreen(
    state: PlatformUiState,
    vm: PlatformViewModel,
    onDownloadStatementPdf: (String) -> Unit
) {
    val tsSnap = state.timesheets
    val canApprove = state.user?.let { it.can("timesheets.approve") && it.can("wages.manage") } == true
    var showSubmitDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showSubmitDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Log Timesheet")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!tsSnap?.statements.isNullOrEmpty()) {
                item {
                    HfSectionCard(title = "Payment Statements") {
                        tsSnap?.statements.orEmpty().forEach { st ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${st.statementNumber} · ${st.staffName}", fontWeight = FontWeight.SemiBold)
                                    Text("${st.periodStart} to ${st.periodEnd} · $${st.totalPayable}", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { onDownloadStatementPdf(st.id) }) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("PDF")
                                }
                            }
                        }
                    }
                }
            }
            items(tsSnap?.timesheets.orEmpty(), key = { it.id }) { t ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${t.staffName} · ${t.workDate}", fontWeight = FontWeight.Bold)
                            StatusBadge(t.status, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            "Regular: ${JobFormat.duration(t.regularMinutes)} · OT: ${JobFormat.duration(t.overtimeMinutes)} · Break: ${t.breakMinutes}m",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (canApprove && t.status == "submitted") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.approveTimesheet(t.id) }) { Text("Approve") }
                                OutlinedButton(onClick = { vm.generatePaymentStatement(t.staffId, t.workDate, t.workDate) }) {
                                    Text("Generate Statement")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubmitDialog) {
        SubmitTimesheetDialog(
            onDismiss = { showSubmitDialog = false },
            onSave = { date, regMins, otMins, breakMins, reimb, notes ->
                showSubmitDialog = false
                vm.submitOwnTimesheet(date, regMins, otMins, breakMins, reimb, notes)
            }
        )
    }
}

@Composable
private fun AnalyticsScreen(state: PlatformUiState, onDownloadCsvExport: () -> Unit) {
    val a = state.analytics
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onDownloadCsvExport,
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Export Financial CSV")
        }
        if (a == null) {
            EmptyStateCard(Icons.Rounded.Analytics, "No analytics loaded", "Pull from top or refresh on Dashboard.")
        } else {
            HfSectionCard(title = "Financial & GST Summary (AUD)") {
                MetricRow("Revenue Excl. GST", "$${a.revenueInvoicedExclGst}")
                MetricRow("GST Collected", "$${a.gstInvoiced}")
                MetricRow("Revenue Incl. GST", "$${a.revenueInvoicedInclGst}")
                MetricRow("Payments Received", "$${a.paymentsReceived}")
                MetricRow("Outstanding Balance", "$${a.outstandingBalance}")
                MetricRow("Overdue (${a.overdueInvoices})", "$${a.overdueBalance}")
                MetricRow("Additional Services", "$${a.additionalCharges}")
            }
            HfSectionCard(title = "Operations & CRM") {
                MetricRow("Jobs Today / This Week", "${a.jobsToday} / ${a.jobsThisWeek}")
                MetricRow("Completed Jobs", a.completedJobs.toString())
                MetricRow("Average Job Duration", JobFormat.duration(a.averageJobDurationMinutes))
                MetricRow("Total Crew Hours", "${a.crewHours} h")
                MetricRow("Repeat Customer Rate", "${(a.customerRepeatRate * 100).toInt()}%")
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NotificationsScreen(
    state: PlatformUiState,
    vm: PlatformViewModel,
    onOpenJob: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp)) {
            OutlinedButton(onClick = { vm.refreshNotificationsOnServer() }) {
                Text("Check for New Alerts")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.notifications.isEmpty()) {
                item {
                    EmptyStateCard(Icons.Rounded.Notifications, "No operational alerts", "All reminders and system alerts are caught up.")
                }
            } else {
                items(state.notifications, key = { it.id }) { n ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (n.readAt == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.markNotificationRead(n.id)
                            n.jobId?.let(onOpenJob)
                        }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(n.body, style = MaterialTheme.typography.bodySmall)
                            Text(n.category.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineQueueScreen(state: PlatformUiState, vm: PlatformViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.flushOfflineQueue() },
                enabled = !state.isOffline && !state.syncBusy && state.pendingOfflineCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (state.syncBusy) "Syncing…" else "Sync Queued Updates")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.queuedOps.isEmpty()) {
                item {
                    EmptyStateCard(Icons.Rounded.Sync, "Offline queue is empty", "Field updates recorded offline appear here with timestamps until synced.")
                }
            } else {
                items(state.queuedOps, key = { it.idempotencyKey }) { op ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(op.label, fontWeight = FontWeight.Bold)
                                StatusBadge(
                                    label = op.status.name,
                                    containerColor = if (op.status == QueueOpStatus.PENDING) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (op.status == QueueOpStatus.PENDING) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text("Recorded: ${op.deviceRecordedAt}", style = MaterialTheme.typography.labelSmall)
                            op.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            if (op.status != QueueOpStatus.PENDING) {
                                TextButton(onClick = { vm.dismissQueuedOp(op.idempotencyKey) }) { Text("Dismiss item") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteStaffDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("driver") }
    var empType by remember { mutableStateOf("employee") }
    var rate by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Staff Invite") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Work email") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("driver", "mover", "subcontractor", "dispatcher").forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("employee", "subcontractor").forEach { e ->
                        FilterChip(selected = empType == e, onClick = { empType = e }, label = { Text(e) })
                    }
                }
                OutlinedTextField(rate, { rate = it }, label = { Text("Hourly rate AUD (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), email.trim(), phone.trim(), role, empType, rate.trim().ifBlank { null }) },
                enabled = name.isNotBlank() && email.contains("@")
            ) { Text("Create Invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NewVehicleDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rego by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf("MR 12t") }
    var tonnes by remember { mutableStateOf("12") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Fleet Vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Truck name (e.g. Truck 2 - Hino)") }, singleLine = true)
                OutlinedTextField(rego, { rego = it }, label = { Text("Registration plate") }, singleLine = true)
                OutlinedTextField(cls, { cls = it }, label = { Text("Class (Small 8t / MR 12t / HR 16t)") }, singleLine = true)
                OutlinedTextField(tonnes, { tonnes = it }, label = { Text("Capacity tonnes") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), rego.trim(), cls.trim(), tonnes.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SubmitTimesheetDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, String, String) -> Unit
) {
    var workDate by remember { mutableStateOf(LocalDate.now(JobFormat.zone).toString()) }
    var regHours by remember { mutableStateOf("8") }
    var otHours by remember { mutableStateOf("0") }
    var breakMins by remember { mutableStateOf("30") }
    var reimb by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Timesheet") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(workDate, { workDate = it }, label = { Text("Date (yyyy-MM-dd)") }, singleLine = true)
                OutlinedTextField(regHours, { regHours = it }, label = { Text("Regular hours") }, singleLine = true)
                OutlinedTextField(otHours, { otHours = it }, label = { Text("Overtime hours") }, singleLine = true)
                OutlinedTextField(breakMins, { breakMins = it }, label = { Text("Break minutes") }, singleLine = true)
                OutlinedTextField(reimb, { reimb = it }, label = { Text("Reimbursements AUD") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val r = ((regHours.toDoubleOrNull() ?: 0.0) * 60).toInt()
                val ot = ((otHours.toDoubleOrNull() ?: 0.0) * 60).toInt()
                val b = breakMins.toIntOrNull() ?: 0
                onSave(workDate, r, ot, b, reimb.trim(), notes.trim())
            }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
