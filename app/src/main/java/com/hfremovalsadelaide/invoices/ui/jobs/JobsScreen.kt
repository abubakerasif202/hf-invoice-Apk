package com.hfremovalsadelaide.invoices.ui.jobs

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.data.StaffMember
import com.hfremovalsadelaide.invoices.data.VehicleRecord
import com.hfremovalsadelaide.invoices.jobs.BookingStatus
import com.hfremovalsadelaide.invoices.jobs.AttentionLevel
import com.hfremovalsadelaide.invoices.jobs.CalendarView
import com.hfremovalsadelaide.invoices.jobs.ClashKind
import com.hfremovalsadelaide.invoices.jobs.DispatchFilter
import com.hfremovalsadelaide.invoices.jobs.DispatchLogic
import com.hfremovalsadelaide.invoices.jobs.JobPriority
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.jobs.JobState
import com.hfremovalsadelaide.invoices.jobs.JobStatusFilter
import com.hfremovalsadelaide.invoices.jobs.JobsSnapshot
import com.hfremovalsadelaide.invoices.jobs.JobsUiState
import com.hfremovalsadelaide.invoices.jobs.SignoffStatus
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import androidx.compose.ui.text.style.TextOverflow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Composable
fun JobsScreen(
    state: JobsUiState,
    onSync: () -> Unit,
    canSyncCalendar: Boolean,
    onRefresh: () -> Unit,
    onSelectView: (CalendarView) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenJob: (String) -> Unit,
    onGoToSignIn: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSearchQueryChanged: (String) -> Unit = {},
    onStatusFilterSelected: (JobStatusFilter) -> Unit = {},
    canDispatch: Boolean = false,
    availableStaff: List<StaffMember> = emptyList(),
    availableVehicles: List<VehicleRecord> = emptyList(),
    onAssignJob: (jobId: String, List<Triple<String?, String, String>>, vehicleId: String?, truck: String?, travelNotes: String, dispatchNotes: String) -> Unit = { _, _, _, _, _, _ -> },
    onRescheduleJob: (jobId: String, startIso: String, endIso: String, reason: String) -> Unit = { _, _, _, _ -> },
    onDispatchFilterChanged: (DispatchFilter) -> Unit = {},
    onSetPriority: (jobId: String, JobPriority) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val snap = state.snapshot
    var assignTarget by remember { mutableStateOf<Job?>(null) }
    var rescheduleTarget by remember { mutableStateOf<Job?>(null) }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(ForestGreen).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Jobs & Dispatch", style = MaterialTheme.typography.titleLarge, color = PureWhite, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            snap == null -> "Loading bookings"
                            state.isFromOfflineCache -> "Showing offline encrypted cache"
                            !snap.calendarConnected -> "Google Calendar not connected"
                            else -> "Calendar synced" + (snap.lastSyncedAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }?.let { " " + JobFormat.time(it) } ?: "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldAccent
                    )
                }
                if (state.loading || state.busy) CircularProgressIndicator(Modifier.size(24.dp), color = GoldAccent, strokeWidth = 2.dp)
                IconButton(onClick = onOpenNotifications, modifier = Modifier.testTag("jobs_notifications_button")) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Job notification settings", tint = PureWhite)
                }
                if (canSyncCalendar) {
                    IconButton(onClick = onSync, enabled = !state.busy, modifier = Modifier.testTag("jobs_sync_button")) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync calendar", tint = PureWhite)
                    }
                }
            }
        }

        when {
            state.authRequired -> CenteredNotice("Sign in required", "Sign in with your HF Removals account to access bookings.", "Sign In", onGoToSignIn)
            snap == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator(color = ForestGreen) else CenteredNotice("Could not load jobs", "Check your connection and try again.", "Retry", onRefresh)
            }
            else -> JobsContent(
                state = state,
                snap = snap,
                onSelectView = onSelectView,
                onSelectDate = onSelectDate,
                onOpenJob = onOpenJob,
                onSearchQueryChanged = onSearchQueryChanged,
                onStatusFilterSelected = onStatusFilterSelected,
                canDispatch = canDispatch,
                onRequestAssign = { assignTarget = it },
                onRequestReschedule = { rescheduleTarget = it },
                onDispatchFilterChanged = onDispatchFilterChanged,
                onSetPriority = onSetPriority
            )
        }
    }

    assignTarget?.let { job ->
        AssignCrewDialog(
            job = job,
            staff = availableStaff,
            vehicles = availableVehicles,
            otherJobs = state.snapshot?.jobs.orEmpty(),
            onDismiss = { assignTarget = null },
            onSave = { assignments, vehicleId, truck, travel, dispatch ->
                assignTarget = null
                onAssignJob(job.id, assignments, vehicleId, truck, travel, dispatch)
            }
        )
    }

    rescheduleTarget?.let { job ->
        RescheduleJobDialog(
            job = job,
            onDismiss = { rescheduleTarget = null },
            onSave = { startIso, endIso, reason ->
                rescheduleTarget = null
                onRescheduleJob(job.id, startIso, endIso, reason)
            }
        )
    }
}

@Composable
private fun JobsContent(
    state: JobsUiState,
    snap: JobsSnapshot,
    onSelectView: (CalendarView) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenJob: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterSelected: (JobStatusFilter) -> Unit,
    canDispatch: Boolean,
    onRequestAssign: (Job) -> Unit,
    onRequestReschedule: (Job) -> Unit,
    onDispatchFilterChanged: (DispatchFilter) -> Unit,
    onSetPriority: (jobId: String, JobPriority) -> Unit
) {
    val live = state.filteredJobs()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("jobs_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!snap.calendarConnected) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    "Google Calendar is not connected yet. The administrator authorises it once from the Web Workspace or /api/google/connect.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        snap.lastError?.let { err -> item { Text("Last sync problem: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }

        // Search plus a status-filter menu on one row: the old five-chip filter row cost a whole line of screen.
        item {
            var filterMenuOpen by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search jobs", maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    Box {
                        IconButton(onClick = { filterMenuOpen = true }, modifier = Modifier.testTag("jobs_filter_menu_button")) {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = "Filter jobs: ${state.statusFilter.label}",
                                tint = if (state.statusFilter == JobStatusFilter.ALL) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary
                            )
                        }
                        DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                            JobStatusFilter.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(if (state.statusFilter == f) "✓ ${f.label}" else f.label) },
                                    onClick = {
                                        filterMenuOpen = false
                                        onStatusFilterSelected(f)
                                    },
                                    modifier = Modifier.testTag("jobs_filter_${f.name.lowercase()}")
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("jobs_search_input")
            )
        }

        item {
            val today = LocalDate.now(JobFormat.zone)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarView.entries.forEach { v ->
                    FilterChip(
                        selected = state.view == v,
                        onClick = { onSelectView(v) },
                        label = { Text(v.label, maxLines = 1, textAlign = TextAlign.Center) },
                        modifier = Modifier.testTag("jobs_view_${v.name.lowercase()}")
                    )
                }
                AssistChip(onClick = { onSelectDate(today) }, label = { Text("Today") }, modifier = Modifier.testTag("jobs_today"))
                AssistChip(onClick = { onSelectDate(today.plusDays(1)) }, label = { Text("Tomorrow") }, modifier = Modifier.testTag("jobs_tomorrow"))
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val stats = listOf(
                    "Today" to snap.todayConfirmed.size,
                    "Upcoming" to snap.upcoming.size,
                    "In progress" to snap.inProgress.size,
                    "Completed" to snap.completed.size,
                    "Sign-off due" to snap.awaitingSignoff.size,
                    "Unconfirmed" to snap.unconfirmed.size,
                    "Missing info" to snap.missingInfoIds.size,
                    "Conflicts" to snap.conflicts.size
                )
                items(stats) { (label, n) ->
                    val alert = (label == "Conflicts" || label == "Unconfirmed" || label == "Sign-off due" || label == "Missing info") && n > 0
                    AssistChip(
                        onClick = {},
                        label = { Text("$label $n", maxLines = 1) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (alert) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.semantics { contentDescription = "$label: $n" }
                    )
                }
            }
        }
        if (snap.conflicts.isNotEmpty()) item {
            val titles = snap.conflicts.joinToString("; ") { (a, b) -> "${snap.jobs.firstOrNull { it.id == a }?.title ?: a} / ${snap.jobs.firstOrNull { it.id == b }?.title ?: b}" }
            Text("Possible crew or truck conflict: $titles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        when (state.view) {
            CalendarView.AGENDA -> agenda(live, state.selectedDate, onOpenJob)
            CalendarView.DAY -> day(live, state, onSelectDate, onOpenJob)
            CalendarView.WEEK -> week(live, state, onSelectDate, onOpenJob)
            CalendarView.MONTH -> month(live, state, onSelectDate, onOpenJob)
            CalendarView.DISPATCH -> dispatchBoard(
                live, state, onSelectDate, onOpenJob, canDispatch, onRequestAssign, onRequestReschedule,
                onDispatchFilterChanged, onSetPriority
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dispatchBoard(
    jobs: List<Job>,
    state: JobsUiState,
    onSelectDate: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
    canDispatch: Boolean,
    onRequestAssign: (Job) -> Unit,
    onRequestReschedule: (Job) -> Unit,
    onFilterChanged: (DispatchFilter) -> Unit,
    onSetPriority: (jobId: String, JobPriority) -> Unit
) {
    item {
        // Short date: the long form wrapped to two lines and pushed the first job below the fold.
        DateNav(JobFormat.day(state.selectedDate), { onSelectDate(state.selectedDate.minusDays(1)) }, { onSelectDate(state.selectedDate.plusDays(1)) })
    }
    item(key = "dispatch_filter") {
        DispatchFilterMenu(filter = state.dispatchFilter, crew = DispatchLogic.crewChoices(jobs), trucks = DispatchLogic.truckChoices(jobs), onChange = onFilterChanged)
    }
    // Multi-day jobs appear on every day they cover; priority then start time orders each lane.
    val dayJobs = DispatchLogic.laneOrder(
        jobs.filter { JobFormat.coversDate(it, state.selectedDate) && DispatchLogic.matches(it, state.dispatchFilter) }
    )
    val conflictIds = state.snapshot?.conflicts.orEmpty().flatMap { listOf(it.first, it.second) }.toSet()
    val now = Instant.now()
    val attention = dayJobs.associate { it.id to DispatchLogic.attention(it, conflictIds, now) }
    val unassignedLane = "Unassigned Truck"
    // Unassigned work goes first so a dispatcher sees it before the allocated lanes.
    val lanes = dayJobs.groupBy { it.truck?.ifBlank { unassignedLane } ?: unassignedLane }
        .toList()
        .sortedBy { (name, _) -> if (name == unassignedLane) 0 else 1 }
    if (dayJobs.isNotEmpty()) {
        item(key = "dispatch_summary") {
            val high = attention.values.count { it.level == AttentionLevel.HIGH }
            val medium = attention.values.count { it.level == AttentionLevel.MEDIUM }
            val subs = dayJobs.count { j -> j.assignmentDetails.orEmpty().any { it.role == "subcontractor" } }
            Text(
                text = "$high action · $medium review · $subs subcontractor",
                style = MaterialTheme.typography.labelMedium,
                color = if (high > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dispatch_summary")
            )
        }
    }
    if (lanes.isEmpty()) {
        item { EmptyText("No bookings on ${JobFormat.day(state.selectedDate)}.") }
    } else {
        lanes.forEach { (truckName, laneJobs) ->
            item(key = "lane_$truckName") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "$truckName (${laneJobs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        laneJobs.forEach { job ->
                            DispatchJobCard(
                                job = job,
                                canDispatch = canDispatch,
                                attention = attention.getValue(job.id),
                                day = state.selectedDate,
                                onOpen = { onOpen(job.id) },
                                onAssign = { onRequestAssign(job) },
                                onReschedule = { onRequestReschedule(job) },
                                onSetPriority = { onSetPriority(job.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One compact menu for every dispatch filter: unassigned, subcontractor jobs, a single truck or a single crew member. */
@Composable
private fun DispatchFilterMenu(filter: DispatchFilter, crew: List<String>, trucks: List<String>, onChange: (DispatchFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val summary = listOfNotNull(
        "Unassigned".takeIf { filter.unassignedOnly },
        "Subcontractors".takeIf { filter.subcontractorsOnly },
        filter.truck,
        filter.crew
    ).joinToString(" · ")
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(if (filter.active) "Filter: $summary" else "Filter", maxLines = 1) },
            leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("dispatch_filter_button")
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(next: DispatchFilter) { open = false; onChange(next) }
            fun check(selected: Boolean, label: String) = if (selected) "✓ $label" else label
            DropdownMenuItem(text = { Text("Show all jobs") }, onClick = { pick(DispatchFilter()) })
            DropdownMenuItem(
                text = { Text(check(filter.unassignedOnly, "Unassigned only")) },
                onClick = { pick(filter.copy(unassignedOnly = !filter.unassignedOnly)) },
                modifier = Modifier.testTag("dispatch_filter_unassigned")
            )
            DropdownMenuItem(
                text = { Text(check(filter.subcontractorsOnly, "Subcontractor jobs")) },
                onClick = { pick(filter.copy(subcontractorsOnly = !filter.subcontractorsOnly)) }
            )
            if (trucks.isNotEmpty()) {
                Text("Truck", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                trucks.forEach { t ->
                    DropdownMenuItem(text = { Text(check(filter.truck.equals(t, true), t)) }, onClick = { pick(filter.copy(truck = if (filter.truck.equals(t, true)) null else t)) })
                }
            }
            if (crew.isNotEmpty()) {
                Text("Crew member", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                crew.forEach { c ->
                    DropdownMenuItem(text = { Text(check(filter.crew.equals(c, true), c)) }, onClick = { pick(filter.copy(crew = if (filter.crew.equals(c, true)) null else c)) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DispatchJobCard(
    job: Job,
    canDispatch: Boolean,
    attention: com.hfremovalsadelaide.invoices.jobs.Attention,
    day: LocalDate,
    onOpen: () -> Unit,
    onAssign: () -> Unit,
    onReschedule: () -> Unit,
    onSetPriority: (JobPriority) -> Unit
) {
    var priorityMenuOpen by remember { mutableStateOf(false) }
    val start = job.effectiveStart
    val end = job.effectiveEnd
    val duration = if (start != null && end != null) JobFormat.duration(Duration.between(start, end).toMinutes().toInt().coerceAtLeast(0)) else "Duration TBC"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.customerName ?: job.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(job)
            }
            val position = JobFormat.dayPosition(job, day)
            val chips = listOfNotNull(
                position?.let { (n, total) -> "Day $n of $total" },
                job.priority.takeIf { it.rank >= JobPriority.HIGH.rank }?.let { "Priority: ${it.label}" }
            )
            if (chips.isNotEmpty()) {
                Text(
                    chips.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (job.priority == JobPriority.URGENT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("dispatch_card_chips")
                )
            }
            Text("${JobFormat.scheduleText(job)} ($duration)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Pickup: ${job.pickup ?: "TBC"} -> Drop-off: ${job.destination ?: "TBC"}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("Crew (${job.crewSize ?: "?"}): ${job.assignees.takeIf { it.isNotEmpty() }?.joinToString() ?: "Unassigned"}", style = MaterialTheme.typography.bodySmall)
            val subcontractors = job.assignmentDetails.orEmpty().filter { it.role == "subcontractor" }.map { it.assignee }
            if (subcontractors.isNotEmpty()) {
                Text("Subcontractors: ${subcontractors.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (attention.level != AttentionLevel.NONE) {
                Text(
                    text = (if (attention.level == AttentionLevel.HIGH) "Needs action: " else "Review: ") + attention.reasons.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (attention.level == AttentionLevel.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("dispatch_attention_${attention.level.name.lowercase()}")
                )
            }
            if (job.travelNotes.isNotBlank()) {
                Text("Travel: ${job.travelNotes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (job.missingInfo.isNotEmpty()) {
                Text("Missing: ${job.missingInfo.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (canDispatch) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAssign) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Assign Crew")
                    }
                    OutlinedButton(onClick = onReschedule) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Reschedule")
                    }
                    Box {
                        OutlinedButton(onClick = { priorityMenuOpen = true }, modifier = Modifier.testTag("dispatch_priority_button")) {
                            Text("Priority: ${job.priority.label}")
                        }
                        DropdownMenu(expanded = priorityMenuOpen, onDismissRequest = { priorityMenuOpen = false }) {
                            JobPriority.entries.reversed().forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(if (p == job.priority) "✓ ${p.label}" else p.label) },
                                    onClick = {
                                        priorityMenuOpen = false
                                        if (p != job.priority) onSetPriority(p)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.agenda(jobs: List<Job>, from: LocalDate, onOpen: (String) -> Unit) {
    val today = LocalDate.now(JobFormat.zone)
    val cutoff = minOf(from, today).minusDays(7)
    // A multi-day job is listed under every day it covers.
    val groups = jobs.flatMap { job -> JobFormat.datesCovered(job).map { it to job } }
        .groupBy({ it.first }, { it.second })
        .filterKeys { !it.isBefore(cutoff) }
        .toSortedMap()
    if (groups.isEmpty()) item { EmptyText("No upcoming bookings found.") }
    groups.forEach { (date, list) ->
        item(key = "header_$date") { DayHeader(date) }
        // The same job can appear on several days, so the key must include the date.
        items(list.sortedBy { it.effectiveStart }, key = { "${date}_${it.id}" }) { JobCard(it, onOpen) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.day(jobs: List<Job>, state: JobsUiState, onSelectDate: (LocalDate) -> Unit, onOpen: (String) -> Unit) {
    item { DateNav(JobFormat.longDay(state.selectedDate), { onSelectDate(state.selectedDate.minusDays(1)) }, { onSelectDate(state.selectedDate.plusDays(1)) }) }
    val list = jobs.filter { JobFormat.coversDate(it, state.selectedDate) }.sortedBy { it.effectiveStart }
    if (list.isEmpty()) item { EmptyText("No jobs on this day.") }
    items(list, key = { it.id }) { JobCard(it, onOpen) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.week(jobs: List<Job>, state: JobsUiState, onSelectDate: (LocalDate) -> Unit, onOpen: (String) -> Unit) {
    val start = state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value - 1).toLong())
    item { DateNav("Week of ${JobFormat.day(start)}", { onSelectDate(state.selectedDate.minusWeeks(1)) }, { onSelectDate(state.selectedDate.plusWeeks(1)) }) }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0L..6L).map { start.plusDays(it) }.forEach { d ->
                val n = jobs.count { JobFormat.coversDate(it, d) }
                DayCell(d, n, d == state.selectedDate, Modifier.weight(1f)) { onSelectDate(d) }
            }
        }
    }
    val list = jobs.filter { JobFormat.coversDate(it, state.selectedDate) }.sortedBy { it.effectiveStart }
    item { DayHeader(state.selectedDate) }
    if (list.isEmpty()) item { EmptyText("No jobs on this day.") }
    items(list, key = { it.id }) { JobCard(it, onOpen) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.month(jobs: List<Job>, state: JobsUiState, onSelectDate: (LocalDate) -> Unit, onOpen: (String) -> Unit) {
    val first = state.selectedDate.withDayOfMonth(1)
    item { DateNav(JobFormat.month(first), { onSelectDate(first.minusMonths(1)) }, { onSelectDate(first.plusMonths(1)) }) }
    val offset = first.dayOfWeek.value - 1
    val cells = List(offset) { null } + (1..first.lengthOfMonth()).map { first.withDayOfMonth(it) }
    items(cells.chunked(7)) { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { d ->
                if (d == null) Spacer(Modifier.weight(1f)) else DayCell(d, jobs.count { JobFormat.coversDate(it, d) }, d == state.selectedDate, Modifier.weight(1f)) { onSelectDate(d) }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    val list = jobs.filter { JobFormat.coversDate(it, state.selectedDate) }.sortedBy { it.effectiveStart }
    item { DayHeader(state.selectedDate) }
    if (list.isEmpty()) item { EmptyText("No jobs on this day.") }
    items(list, key = { "m" + it.id }) { JobCard(it, onOpen) }
}

@Composable
private fun DayCell(date: LocalDate, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) ForestGreen else MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${JobFormat.longDay(date)}, $count jobs" }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(date.dayOfMonth.toString(), color = if (selected) PureWhite else MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
        Text(if (count > 0) "$count" else "", color = if (selected) GoldAccent else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DateNav(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous") }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next") }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(JobFormat.longDay(date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun EmptyText(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

@Composable
fun JobCard(job: Job, onOpen: (String) -> Unit) {
    Card(
        onClick = { onOpen(job.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("job_card_${job.id}")
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.title.ifBlank { "Untitled booking" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                StatusPill(job)
            }
            Text(JobFormat.scheduleText(job), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            job.pickup?.let { Text("From: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            if (job.assignees.isNotEmpty() || !job.truck.isNullOrBlank()) {
                Text(
                    listOfNotNull(job.truck, job.assignees.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (job.notice) {
                Text("Notice (not a job)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (job.bookingStatus != BookingStatus.CONFIRMED) {
                Text(
                    if (job.bookingStatus == BookingStatus.UNCONFIRMED) "Unconfirmed details" else "Booking not marked confirmed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun StatusPill(job: Job) {
    val (text, bg, fg) = when {
        job.cancelled -> Triple("Cancelled", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        job.state == JobState.IN_PROGRESS -> Triple("In progress", GoldAccent, PureWhite)
        job.state == JobState.PAUSED -> Triple("Paused", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        job.state == JobState.COMPLETED && job.signoff == SignoffStatus.SIGNED -> Triple("Signed", ForestGreen, PureWhite)
        job.state == JobState.COMPLETED && job.signoff == SignoffStatus.SKIPPED -> Triple("Completed · not signed", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        job.state == JobState.COMPLETED -> Triple("Awaiting sign-off", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else -> Triple("Scheduled", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CenteredNotice(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)) { Text(action) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignCrewDialog(
    job: Job,
    staff: List<StaffMember>,
    vehicles: List<VehicleRecord>,
    onDismiss: () -> Unit,
    onSave: (List<Triple<String?, String, String>>, String?, String?, String, String) -> Unit,
    otherJobs: List<Job> = emptyList()
) {
    var selectedVehicleId by remember { mutableStateOf(job.vehicleId) }
    var truckText by remember { mutableStateOf(job.truck.orEmpty()) }
    val existingStaff = job.assignmentDetails.orEmpty().mapNotNull { assignment ->
        assignment.staffId?.let { it to assignment }
    }.toMap()
    val existingManualCrew = job.assignmentDetails.orEmpty().filter { it.staffId == null }
    var selectedStaff by remember(job.id) { mutableStateOf(job.staffIds.toSet()) }
    var manualCrew by remember(job.id) { mutableStateOf(existingManualCrew.toSet()) }
    var extraAssignee by remember(job.id) { mutableStateOf("") }
    var travelNotes by remember { mutableStateOf(job.travelNotes) }
    var dispatchNotes by remember { mutableStateOf(job.dispatchNotes) }
    val canEditExistingCrew = job.assignmentDetails != null || job.assignees.isEmpty()
    val assignments = buildList<Triple<String?, String, String>> {
        selectedStaff.forEach { id ->
            val current = existingStaff[id]
            val member = staff.firstOrNull { it.id == id }
            val name = member?.name ?: current?.assignee
            if (name != null) {
                val role = current?.role ?: when (member?.role) {
                    "driver" -> "driver"
                    "subcontractor" -> "subcontractor"
                    else -> "mover"
                }
                add(Triple(id, name, role))
            }
        }
        manualCrew.forEach { add(Triple(null, it.assignee, it.role)) }
        if (extraAssignee.isNotBlank()) add(Triple(null, extraAssignee.trim(), "subcontractor"))
    }
    val duplicateNames = assignments.map { it.second }.distinct().size != assignments.size
    val clashes = DispatchLogic.clashes(
        job = job,
        others = otherJobs,
        staffIds = selectedStaff,
        crewNames = (manualCrew.map { it.assignee } + listOfNotNull(extraAssignee.trim().ifBlank { null })).toSet() +
            selectedStaff.mapNotNull { id -> staff.firstOrNull { it.id == id }?.name },
        vehicleId = selectedVehicleId,
        truck = truckText.trim().ifBlank { null }
    ).distinctBy { it.kind to it.otherJobId }
    val selectedVehicle = vehicles.firstOrNull { it.id == selectedVehicleId }
    val vehicleUnavailable = selectedVehicle != null && selectedVehicle.status != "available"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Crew & Vehicle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vehicles.isNotEmpty()) {
                    Text("Select Truck", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vehicles.forEach { v ->
                            FilterChip(
                                selected = selectedVehicleId == v.id,
                                onClick = {
                                    selectedVehicleId = v.id
                                    truckText = "${v.name} (${v.vehicleClass})"
                                },
                                label = { Text(v.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(truckText, { truckText = it }, label = { Text("Truck package") }, singleLine = true)
                if (staff.isNotEmpty()) {
                    Text("Select Movers / Subcontractors", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        staff.filter { it.active || it.id in selectedStaff }.forEach { member ->
                            val isSelected = member.id in selectedStaff
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedStaff = if (isSelected) selectedStaff - member.id else selectedStaff + member.id },
                                label = { Text("${member.name} (${member.role})") }
                            )
                        }
                    }
                }
                if (existingManualCrew.isNotEmpty()) {
                    Text("Manually assigned crew", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingManualCrew.forEach { assignment ->
                            FilterChip(
                                selected = assignment in manualCrew,
                                onClick = {
                                    manualCrew = if (assignment in manualCrew) manualCrew - assignment else manualCrew + assignment
                                },
                                label = { Text("${assignment.assignee} (${assignment.role})") }
                            )
                        }
                    }
                }
                OutlinedTextField(extraAssignee, { extraAssignee = it }, label = { Text("Additional crew/subcontractor name") }, singleLine = true)
                if (!canEditExistingCrew) {
                    Text("Refresh this booking before editing the crew.", color = MaterialTheme.colorScheme.error)
                }
                if (duplicateNames) {
                    Text("Each crew member must have a distinct name on this booking.", color = MaterialTheme.colorScheme.error)
                }
                if (vehicleUnavailable) {
                    Text(
                        "${selectedVehicle?.name} is marked ${selectedVehicle?.status}.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("assign_vehicle_unavailable")
                    )
                }
                clashes.forEach { clash ->
                    val what = if (clash.kind == ClashKind.CREW) "Crew member" else "Truck"
                    Text(
                        "$what ${clash.label.ifBlank { "selected" }} is already on \"${clash.otherTitle}\" at the same time.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("assign_clash_warning")
                    )
                }
                OutlinedTextField(travelNotes, { travelNotes = it }, label = { Text("Travel considerations") })
                OutlinedTextField(dispatchNotes, { dispatchNotes = it }, label = { Text("Dispatch instructions") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(assignments, selectedVehicleId, truckText.trim().ifBlank { null }, travelNotes, dispatchNotes) },
                enabled = canEditExistingCrew && !duplicateNames
            ) { Text(if (clashes.isNotEmpty() || vehicleUnavailable) "Save Anyway" else "Save Assignment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RescheduleJobDialog(
    job: Job,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var startText by remember { mutableStateOf(JobFormat.editable(job.effectiveStart)) }
    var endText by remember { mutableStateOf(JobFormat.editable(job.effectiveEnd)) }
    var reason by remember { mutableStateOf("") }
    val pStart = JobFormat.parseEditable(startText)
    val pEnd = JobFormat.parseEditable(endText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Dispatch Window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Updates the operational dispatch time while preserving an audit log.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(startText, { startText = it }, label = { Text("Start (yyyy-MM-dd HH:mm Adelaide)") }, isError = pStart == null, singleLine = true)
                OutlinedTextField(endText, { endText = it }, label = { Text("End (yyyy-MM-dd HH:mm Adelaide)") }, isError = pEnd == null, singleLine = true)
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(pStart!!.toString(), pEnd!!.toString(), reason) },
                enabled = pStart != null && pEnd != null
            ) { Text("Reschedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
