package com.hfremovalsadelaide.invoices.ui.jobs

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.jobs.BookingStatus
import com.hfremovalsadelaide.invoices.jobs.CalendarView
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.jobs.JobFormat
import com.hfremovalsadelaide.invoices.jobs.JobState
import com.hfremovalsadelaide.invoices.jobs.JobsSnapshot
import com.hfremovalsadelaide.invoices.jobs.JobsUiState
import com.hfremovalsadelaide.invoices.jobs.SignoffStatus
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.ForestSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.MutedSage
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.ui.theme.SageOutline
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JobsScreen(
    state: JobsUiState,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
    onSelectView: (CalendarView) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenJob: (String) -> Unit,
    onGoToSignIn: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snap = state.snapshot
    Column(modifier = modifier.background(PureWhite)) {
        Box(Modifier.fillMaxWidth().background(ForestGreen).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Jobs", style = MaterialTheme.typography.titleLarge, color = PureWhite, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            snap == null -> "Loading bookings"
                            !snap.calendarConnected -> "Google Calendar not connected"
                            else -> "Calendar synced" + (snap.lastSyncedAt?.let { " " + it.take(16).replace('T', ' ') + " UTC" } ?: "")
                        },
                        style = MaterialTheme.typography.bodySmall, color = GoldAccent
                    )
                }
                if (state.loading || state.busy) CircularProgressIndicator(Modifier.size(24.dp), color = GoldAccent, strokeWidth = 2.dp)
                IconButton(onClick = onOpenNotifications, modifier = Modifier.testTag("jobs_notifications_button")) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Job notification settings", tint = PureWhite)
                }
                IconButton(onClick = onSync, enabled = !state.busy, modifier = Modifier.testTag("jobs_sync_button")) {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync calendar", tint = PureWhite)
                }
            }
        }

        when {
            state.authRequired -> CenteredNotice("Sign in required", "Sign in from the Workspace tab, then return here.", "Go to Workspace", onGoToSignIn)
            snap == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator(color = ForestGreen) else CenteredNotice("Could not load jobs", "Check your connection and try again.", "Retry", onRefresh)
            }
            else -> JobsContent(state, snap, onSelectView, onSelectDate, onOpenJob, onRefresh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun JobsContent(
    state: JobsUiState,
    snap: JobsSnapshot,
    onSelectView: (CalendarView) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenJob: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val live = snap.jobs.filter { !it.cancelled }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("jobs_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!snap.calendarConnected) item {
            Card(colors = CardDefaults.cardColors(containerColor = ForestSoftTint)) {
                Text(
                    "Google Calendar is not connected yet. The administrator authorises it once by signing in to the website in a browser and opening /api/google/connect.",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = ForestCanopy
                )
            }
        }
        snap.lastError?.let { err -> item { Text("Last sync problem: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val stats = listOf(
                    "Today" to snap.todayConfirmed.size, "Upcoming" to snap.upcoming.size, "In progress" to snap.inProgress.size,
                    "Completed" to snap.completed.size, "Sign-off due" to snap.awaitingSignoff.size,
                    "Unconfirmed" to snap.unconfirmed.size, "Conflicts" to snap.conflicts.size
                )
                items(stats) { (label, n) ->
                    val alert = (label == "Conflicts" || label == "Unconfirmed" || label == "Sign-off due") && n > 0
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (alert) GoldAccent.copy(alpha = 0.22f) else ForestSoftTint),
                        modifier = Modifier.semantics { contentDescription = "$label: $n" }
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(n.toString(), style = MaterialTheme.typography.headlineSmall, color = ForestGreen, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = ForestCanopy)
                        }
                    }
                }
            }
        }
        if (snap.conflicts.isNotEmpty()) item {
            val titles = snap.conflicts.joinToString("; ") { (a, b) -> "${snap.jobs.firstOrNull { it.id == a }?.title ?: a} / ${snap.jobs.firstOrNull { it.id == b }?.title ?: b}" }
            Text("Possible crew or truck conflict: $titles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarView.entries.forEach { v ->
                    FilterChip(selected = state.view == v, onClick = { onSelectView(v) }, label = { Text(v.label) }, modifier = Modifier.testTag("jobs_view_${v.name.lowercase()}"))
                }
            }
        }
        item {
            val today = LocalDate.now(JobFormat.zone)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onSelectDate(today) }, label = { Text("Today") }, modifier = Modifier.testTag("jobs_today"))
                AssistChip(onClick = { onSelectDate(today.plusDays(1)) }, label = { Text("Tomorrow") }, modifier = Modifier.testTag("jobs_tomorrow"))
            }
        }

        when (state.view) {
            CalendarView.AGENDA -> agenda(live, state.selectedDate, onOpenJob)
            CalendarView.DAY -> day(live, state, onSelectDate, onOpenJob)
            CalendarView.WEEK -> week(live, state, onSelectDate, onOpenJob)
            CalendarView.MONTH -> month(live, state, onSelectDate, onOpenJob)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.agenda(jobs: List<Job>, from: LocalDate, onOpen: (String) -> Unit) {
    val today = LocalDate.now(JobFormat.zone)
    val groups = jobs.groupBy { JobFormat.localDate(it) }.filterKeys { it != null && !it.isBefore(minOf(from, today).minusDays(0)) }.toSortedMap(compareBy { it })
    if (groups.isEmpty()) item { EmptyText("No upcoming bookings found.") }
    groups.forEach { (date, list) ->
        item { DayHeader(date!!) }
        items(list.sortedBy { it.scheduledStart }, key = { it.id }) { JobCard(it, onOpen) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.day(jobs: List<Job>, state: JobsUiState, onSelectDate: (LocalDate) -> Unit, onOpen: (String) -> Unit) {
    item { DateNav(JobFormat.longDay(state.selectedDate), { onSelectDate(state.selectedDate.minusDays(1)) }, { onSelectDate(state.selectedDate.plusDays(1)) }) }
    val list = jobs.filter { JobFormat.localDate(it) == state.selectedDate }.sortedBy { it.scheduledStart }
    if (list.isEmpty()) item { EmptyText("No jobs on this day.") }
    items(list, key = { it.id }) { JobCard(it, onOpen) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.week(jobs: List<Job>, state: JobsUiState, onSelectDate: (LocalDate) -> Unit, onOpen: (String) -> Unit) {
    val start = state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value - 1).toLong())
    item {
        DateNav("Week of ${JobFormat.day(start)}", { onSelectDate(state.selectedDate.minusWeeks(1)) }, { onSelectDate(state.selectedDate.plusWeeks(1)) })
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0L..6L).map { start.plusDays(it) }.forEach { d ->
                val n = jobs.count { JobFormat.localDate(it) == d }
                DayCell(d, n, d == state.selectedDate, Modifier.weight(1f)) { onSelectDate(d) }
            }
        }
    }
    val list = jobs.filter { JobFormat.localDate(it) == state.selectedDate }.sortedBy { it.scheduledStart }
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
                if (d == null) Spacer(Modifier.weight(1f)) else DayCell(d, jobs.count { JobFormat.localDate(it) == d }, d == state.selectedDate, Modifier.weight(1f)) { onSelectDate(d) }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    val list = jobs.filter { JobFormat.localDate(it) == state.selectedDate }.sortedBy { it.scheduledStart }
    item { DayHeader(state.selectedDate) }
    if (list.isEmpty()) item { EmptyText("No jobs on this day.") }
    items(list, key = { "m" + it.id }) { JobCard(it, onOpen) }
}

@Composable
private fun DayCell(date: LocalDate, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) ForestGreen else ForestSoftTint)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${JobFormat.longDay(date)}, $count jobs" }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(date.dayOfMonth.toString(), color = if (selected) PureWhite else ForestCanopy, fontWeight = FontWeight.SemiBold)
        Text(if (count > 0) "$count" else "", color = if (selected) GoldAccent else ForestGreen, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DateNav(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous") }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ForestGreen, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next") }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(JobFormat.longDay(date), style = MaterialTheme.typography.titleSmall, color = ForestGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun EmptyText(text: String) = Text(text, color = MutedSage, style = MaterialTheme.typography.bodyMedium)

@Composable
fun JobCard(job: Job, onOpen: (String) -> Unit) {
    Card(
        onClick = { onOpen(job.id) },
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, SageOutline),
        modifier = Modifier.fillMaxWidth().testTag("job_card_${job.id}")
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.title.ifBlank { "Untitled booking" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ForestGreen, fontWeight = FontWeight.SemiBold, maxLines = 2)
                StatusPill(job)
            }
            Text(JobFormat.scheduleText(job), style = MaterialTheme.typography.bodyMedium, color = ForestCanopy)
            job.pickup?.let { Text("From: $it", style = MaterialTheme.typography.bodySmall, color = MutedSage, maxLines = 1) }
            if (job.bookingStatus != BookingStatus.CONFIRMED) {
                Text(if (job.bookingStatus == BookingStatus.UNCONFIRMED) "Unconfirmed details" else "Booking not marked confirmed", style = MaterialTheme.typography.labelMedium, color = GoldAccent.copy(red = 0.55f, green = 0.4f, blue = 0.1f))
            }
        }
    }
}

@Composable
private fun StatusPill(job: Job) {
    val (text, bg) = when {
        job.state == JobState.IN_PROGRESS -> "In progress" to GoldAccent
        job.state == JobState.PAUSED -> "Paused" to GoldAccent.copy(alpha = 0.6f)
        job.state == JobState.COMPLETED && job.signoff == SignoffStatus.SIGNED -> "Signed" to ForestGreen
        job.state == JobState.COMPLETED && job.signoff == SignoffStatus.SKIPPED -> "Completed · not signed" to ForestCanopy
        job.state == JobState.COMPLETED -> "Awaiting sign-off" to ForestCanopy
        else -> "Scheduled" to ForestSoftTint
    }
    val fg = if (bg == ForestSoftTint || bg.alpha < 1f || bg == GoldAccent) ForestCanopy else PureWhite
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CenteredNotice(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = ForestGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MutedSage)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)) { Text(action) }
    }
}
