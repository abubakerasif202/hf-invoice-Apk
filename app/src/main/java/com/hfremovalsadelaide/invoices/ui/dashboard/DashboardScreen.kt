package com.hfremovalsadelaide.invoices.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.data.AnalyticsSnapshot
import com.hfremovalsadelaide.invoices.data.MonthlyTrendPoint
import com.hfremovalsadelaide.invoices.data.OperationsSnapshot
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.jobs.JobsSnapshot
import com.hfremovalsadelaide.invoices.ui.PlatformUiState
import com.hfremovalsadelaide.invoices.ui.components.EmptyStateCard
import com.hfremovalsadelaide.invoices.ui.components.HfSectionCard
import com.hfremovalsadelaide.invoices.ui.components.OfflineAndSyncBanner
import com.hfremovalsadelaide.invoices.ui.components.SkeletonListPlaceholder
import com.hfremovalsadelaide.invoices.ui.theme.ForestActive
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import java.time.LocalDate

@Composable
private fun DashboardHeader(
    name: String,
    subtitle: String,
    unreadCount: Int,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(ForestCanopy, ForestGreen)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val stackActions = maxWidth < 52.dp + 144.dp + 160.dp * LocalDensity.current.fontScale
        val actions: @Composable () -> Unit = {
            DashboardHeaderActions(unreadCount, onRefresh, onOpenNotifications, onOpenSettings)
        }
        if (stackActions) {
            Column(horizontalAlignment = Alignment.End) {
                DashboardIdentity(name, null, Modifier.fillMaxWidth())
                DashboardSubtitle(subtitle, Modifier.fillMaxWidth())
                actions()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                DashboardIdentity(name, subtitle, Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
private fun DashboardIdentity(name: String, subtitle: String?, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.85f)),
            modifier = Modifier.size(40.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.hf_logo),
                contentDescription = "HF Removals Adelaide",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) DashboardSubtitle(subtitle)
        }
    }
}

@Composable
private fun DashboardSubtitle(subtitle: String, modifier: Modifier = Modifier) {
    Text(subtitle, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = GoldAccent)
}

@Composable
private fun DashboardHeaderActions(
    unreadCount: Int,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row {
        IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh dashboard", tint = PureWhite)
        }
        IconButton(onClick = onOpenNotifications, modifier = Modifier.size(48.dp).testTag("dashboard_notifications_btn")) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(containerColor = GoldAccent, contentColor = ForestCanopy) {
                            Text(unreadCount.coerceAtMost(9).toString())
                        }
                    }
                }
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = PureWhite)
            }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp).testTag("dashboard_settings_btn")) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = PureWhite)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    platformState: PlatformUiState,
    jobsSnapshot: JobsSnapshot?,
    jobsLoading: Boolean,
    onRefreshAll: () -> Unit,
    onOpenSignIn: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenOfflineQueue: () -> Unit,
    onOpenJob: (String) -> Unit,
    onNavigateJobsDispatch: () -> Unit,
    onNavigateNewInvoice: () -> Unit,
    onNavigateAnalytics: () -> Unit,
    onNavigateTimesheets: () -> Unit,
    onViewCalendar: () -> Unit = {},
    onAddCustomer: () -> Unit = {},
    onCreateQuotation: () -> Unit = {},
    onSyncBookings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val user = platformState.user
    val analytics = platformState.analytics
    val today = LocalDate.now(JobFormat.zone)
    val todayJobs = jobsSnapshot?.jobs
        ?.filter { !it.cancelled && JobFormat.localDate(it) == today }
        ?.sortedBy { it.scheduledStart }
        .orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_screen")
    ) {
        DashboardHeader(
            name = user?.name ?: "HF Removals",
            subtitle = user?.role?.displayLabel ?: "Operations & Dispatch",
            unreadCount = platformState.unreadNotificationsCount,
            onRefresh = onRefreshAll,
            onOpenNotifications = onOpenNotifications,
            onOpenSettings = onOpenSettings
        )

        OfflineAndSyncBanner(
            isOffline = platformState.isOffline,
            pendingCount = platformState.pendingOfflineCount,
            conflictCount = platformState.conflictOfflineCount,
            syncBusy = platformState.syncBusy,
            onSyncNow = onSyncNow,
            onOpenQueue = onOpenOfflineQueue
        )

        if (jobsLoading && jobsSnapshot == null && analytics == null) {
            SkeletonListPlaceholder(count = 4)
            return
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 920.dp)
            ) {
                // Sign-in prompt when no authenticated user session is loaded
                if (user == null) {
                    item(key = "sign_in_prompt") {
                        HfSectionCard(title = "Sign In Required") {
                            Text(
                                text = "Sign in with your HF Removals staff or administrator account to load live bookings, dispatch, customers, and invoices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.material3.Button(
                                onClick = onOpenSignIn,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = ForestGreen,
                                    contentColor = PureWhite
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("dashboard_sign_in_btn")
                            ) {
                                Text("Sign In to Workspace", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Quick Actions strip
                item(key = "quick_actions") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = onNavigateJobsDispatch,
                            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(if (user?.can("jobs.assign") == true) "Assign Crew" else "My Schedule") },
                            modifier = Modifier.testTag("quick_action_dispatch")
                        )
                        if (user != null) {
                            AssistChip(
                                onClick = onViewCalendar,
                                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("Calendar") },
                                modifier = Modifier.testTag("quick_action_calendar")
                            )
                        }
                        if (user?.can("calendar.sync") == true) {
                            AssistChip(
                                onClick = onSyncBookings,
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("Sync Bookings") },
                                modifier = Modifier.testTag("quick_action_sync_bookings")
                            )
                        }
                        if (user?.can("crm.manage") == true) {
                            AssistChip(
                                onClick = onAddCustomer,
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("Add Customer") },
                                modifier = Modifier.testTag("quick_action_add_customer")
                            )
                            AssistChip(
                                onClick = onCreateQuotation,
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("New Quotation") },
                                modifier = Modifier.testTag("quick_action_new_quote")
                            )
                        }
                        if (user?.can("invoices.manage") == true) {
                            AssistChip(
                                onClick = onNavigateNewInvoice,
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("New Invoice") }
                            )
                        }
                        if (user?.can("reports.read") == true) {
                            AssistChip(
                                onClick = onNavigateAnalytics,
                                leadingIcon = { Icon(Icons.Rounded.Analytics, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("Reports & GST") }
                            )
                        }
                        AssistChip(
                            onClick = onNavigateTimesheets,
                            label = { Text("Timesheets") }
                        )
                    }
                }

                // Role-appropriate KPI grid
                item(key = "kpis") {
                    DashboardKpiGrid(
                        platformState = platformState,
                        jobsSnapshot = jobsSnapshot,
                        analytics = analytics
                    )
                }

                // Live operations snapshot (server-computed; absent when the backend predates it)
                val operations = analytics?.operations
                if (operations != null && user?.can("reports.read") == true) {
                    item(key = "operations") {
                        OperationsOverview(operations = operations, jobsSnapshot = jobsSnapshot, onOpenDispatch = onNavigateJobsDispatch)
                    }
                }

                // Revenue trend visualization (Owners / Admins with analytics.read)
                if (analytics != null && user?.can("reports.read") == true && analytics.monthlyTrends.isNotEmpty()) {
                    item(key = "revenue_chart") {
                        HfSectionCard(title = "Monthly Invoiced vs Received (AUD)") {
                            RevenueTrendChart(trends = analytics.monthlyTrends)
                            if (analytics.truckUtilisation.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Truck Utilisation",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val maxMins = analytics.truckUtilisation.maxOfOrNull { it.workingMinutes }?.coerceAtLeast(60) ?: 60
                                analytics.truckUtilisation.take(4).forEach { truck ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(truck.truck, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                "${truck.jobs} jobs · ${JobFormat.duration(truck.workingMinutes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { (truck.workingMinutes.toFloat() / maxMins.toFloat()).coerceIn(0f, 1f) },
                                            color = ForestGreen,
                                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Operational Alerts (only genuine alerts reported by server)
                val conflictCount = jobsSnapshot?.conflicts?.size ?: 0
                val missingCount = jobsSnapshot?.missingInfoIds?.size ?: 0
                val fleetAlerts = platformState.fleet?.alerts.orEmpty()
                val fleetOverlaps = platformState.fleet?.overlaps.orEmpty()
                val pendingPortal = platformState.crm?.portalSubmissions?.count { it.status == "pending" } ?: 0

                if (conflictCount > 0 || missingCount > 0 || fleetAlerts.isNotEmpty() || fleetOverlaps.isNotEmpty() || pendingPortal > 0) {
                    item(key = "alerts") {
                        HfSectionCard(
                            title = "Attention Required",
                            trailing = {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = GoldAccent)
                            }
                        ) {
                            if (conflictCount > 0 || fleetOverlaps.isNotEmpty()) {
                                Text(
                                    "• ${maxOf(conflictCount, fleetOverlaps.size)} potential truck/crew overlap(s) detected on Dispatch Board",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (missingCount > 0) {
                                Text(
                                    "• $missingCount booking(s) missing truck, crew, pickup, or confirmation details",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            fleetAlerts.take(3).forEach { alert ->
                                Text(
                                    "• ${alert.vehicleName}: ${alert.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (pendingPortal > 0) {
                                Text(
                                    "• $pendingPortal customer portal submission(s) waiting for staff review",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Today's Schedule
                item(key = "today_header") {
                    Text(
                        text = "Today's Schedule (${todayJobs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (todayJobs.isEmpty()) {
                    item(key = "today_empty") {
                        EmptyStateCard(
                            icon = Icons.Rounded.CalendarMonth,
                            title = "No jobs scheduled today",
                            message = "Open the Jobs calendar or Dispatch Board to check upcoming bookings."
                        )
                    }
                } else {
                    items(todayJobs, key = { it.id }) { job ->
                        TodayJobQuickCard(job = job, onClick = { onOpenJob(job.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiGrid(
    platformState: PlatformUiState,
    jobsSnapshot: JobsSnapshot?,
    analytics: AnalyticsSnapshot?
) {
    val user = platformState.user
    val cards = if (analytics != null && user?.can("reports.read") == true) {
        listOf(
            KpiCardData("Invoiced (incl GST)", "$${analytics.revenueInvoicedInclGst}", false),
            KpiCardData("Payments Received", "$${analytics.paymentsReceived}", false),
            KpiCardData(
                "Outstanding",
                "$${analytics.outstandingBalance}",
                analytics.overdueInvoices > 0,
                hint = if (analytics.overdueInvoices > 0) "${analytics.overdueInvoices} overdue · $${analytics.overdueBalance}" else null
            ),
            KpiCardData("Jobs Today", analytics.jobsToday.toString(), false)
        )
    } else {
        listOf(
            KpiCardData("Today", (jobsSnapshot?.todayConfirmed?.size ?: 0).toString(), false),
            KpiCardData("In Progress", (jobsSnapshot?.inProgress?.size ?: 0).toString(), false),
            KpiCardData("Upcoming", (jobsSnapshot?.upcoming?.size ?: 0).toString(), false),
            KpiCardData(
                "Sign-off Due",
                (jobsSnapshot?.awaitingSignoff?.size ?: 0).toString(),
                (jobsSnapshot?.awaitingSignoff?.isNotEmpty() == true)
            )
        )
    }

    KpiCardGrid(cards)
}

@Composable
private fun KpiCardGrid(cards: List<KpiCardData>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { card ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (card.highlight) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = card.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Money values must never be ellipsised: shrink the text instead.
                            val baseValueStyle = MaterialTheme.typography.titleLarge
                            var valueStyle by remember(card.value, baseValueStyle) { mutableStateOf(baseValueStyle) }
                            Text(
                                text = card.value,
                                style = valueStyle,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                onTextLayout = { layout ->
                                    if (layout.hasVisualOverflow && valueStyle.fontSize > 14.sp) {
                                        valueStyle = valueStyle.copy(fontSize = valueStyle.fontSize * 0.9f)
                                    }
                                }
                            )
                            if (card.hint != null) {
                                Text(
                                    text = card.hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationsOverview(operations: OperationsSnapshot, jobsSnapshot: JobsSnapshot?, onOpenDispatch: () -> Unit) {
    HfSectionCard(title = "Operations") {
        KpiCardGrid(operationsTiles(operations))
        val titleOf = { id: String -> jobsSnapshot?.jobs?.firstOrNull { it.id == id }?.title?.ifBlank { null } ?: "Booking" }
        operations.crewConflicts.take(3).forEach { c ->
            Text(
                text = "• ${titleOf(c.jobA)} overlaps ${titleOf(c.jobB)} (${c.reasons.joinToString(" + ")})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onOpenDispatch)
            )
        }
        if (operations.crewConflicts.size > 3) {
            Text(
                text = "+${operations.crewConflicts.size - 3} more conflicts on the Dispatch Board",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        operations.maintenanceDue.take(3).forEach { m ->
            Text(
                text = "• ${m.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun operationsTiles(o: OperationsSnapshot): List<KpiCardData> = listOf(
    KpiCardData("Confirmed Today", o.confirmedToday.toString(), false, o.tentativeToday.takeIf { it > 0 }?.let { "+$it tentative" }),
    KpiCardData("Confirmed Tomorrow", o.confirmedTomorrow.toString(), false, o.tentativeTomorrow.takeIf { it > 0 }?.let { "+$it tentative" }),
    KpiCardData("Active Jobs", o.activeJobs.toString(), false),
    KpiCardData(
        "Unassigned",
        o.unassignedBookings.toString(),
        o.unassignedBookings > 0,
        if (o.unassignedBookings > 0) "${o.unassignedCrew} need crew · ${o.unassignedTruck} need truck" else null
    ),
    KpiCardData(
        "Trucks Available",
        "${o.trucksAvailableToday} of ${o.trucksTotal}",
        false,
        "${o.trucksOnJobsToday} on jobs · ${o.trucksUnavailable} unavailable"
    ),
    KpiCardData("Staff Working Today", o.workingStaffToday.toString(), false),
    KpiCardData("Crew Conflicts", o.crewConflicts.size.toString(), o.crewConflicts.isNotEmpty()),
    KpiCardData("Maintenance Due", o.maintenanceDue.size.toString(), o.maintenanceDue.isNotEmpty(), "Rego 30d · service 14d")
)

private data class KpiCardData(val label: String, val value: String, val highlight: Boolean, val hint: String? = null)

@Composable
private fun RevenueTrendChart(trends: List<MonthlyTrendPoint>) {
    val recent = trends.takeLast(6)
    val invoicedValues = recent.map { it.invoicedInclGst.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
    val paidValues = recent.map { it.paymentsReceived.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
    val peak = (invoicedValues + paidValues).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot("Invoiced", ForestGreen)
            LegendDot("Payments", GoldAccent)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .padding(top = 6.dp)
        ) {
            val groupCount = recent.size.coerceAtLeast(1)
            val groupWidth = size.width / groupCount
            val barWidth = (groupWidth * 0.28f).coerceAtLeast(8.dp.toPx())
            val gap = (groupWidth * 0.08f).coerceAtLeast(3.dp.toPx())
            val chartH = size.height - 6.dp.toPx()

            for (i in recent.indices) {
                val invH = ((invoicedValues[i] / peak) * chartH).toFloat().coerceAtLeast(4f)
                val paidH = ((paidValues[i] / peak) * chartH).toFloat().coerceAtLeast(4f)
                val baseX = i * groupWidth + (groupWidth - (barWidth * 2 + gap)) / 2f

                drawRoundRect(
                    color = ForestActive,
                    topLeft = Offset(baseX, size.height - invH),
                    size = Size(barWidth, invH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = GoldAccent,
                    topLeft = Offset(baseX + barWidth + gap, size.height - paidH),
                    size = Size(barWidth, paidH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            recent.forEach { pt ->
                Text(
                    text = pt.month.takeLast(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodayJobQuickCard(job: Job, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.title.ifBlank { "Untitled Job" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = job.state.server.replace('_', ' ').uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "${JobFormat.scheduleText(job)} · ${job.truck ?: "No truck"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!job.pickup.isNullOrBlank()) {
                Text(
                    text = "${job.pickup} -> ${job.destination ?: "TBC"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
