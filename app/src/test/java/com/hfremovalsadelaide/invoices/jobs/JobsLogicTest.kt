package com.hfremovalsadelaide.invoices.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JobsLogicTest {

    private val payload = """
        {"jobs":[
          {"id":"j1","title":"Removal - Jane","scheduledStart":"2026-11-24T22:30:00.000Z","scheduledEnd":"2026-11-25T02:30:00.000Z",
           "dateOnly":false,"serviceDate":null,"timeZone":"Australia/Adelaide","bookingStatus":"confirmed","cancelled":false,"state":"in_progress",
           "signoffStatus":"none","assignees":["sam"],"truck":"MR Truck",
           "details":{"reference":"HF-1","customerName":"Jane","customerPhone":"0412 000 000","pickup":"1 A St","destination":"2 B St","truck":"MR Truck","crewSize":2,"notes":null},
           "actual":{"start":"2026-11-24T22:40:00.000Z","finish":null,"elapsedMinutes":30,"breakMinutes":5,"workingMinutes":25}},
          {"id":"j2","title":"Dispatch","scheduledStart":null,"scheduledEnd":null,"dateOnly":true,"serviceDate":"2026-11-26","timeZone":"Australia/Adelaide",
           "bookingStatus":"unknown","cancelled":false,"state":"scheduled","signoffStatus":"none","assignees":[],"truck":null,"details":{},
           "actual":{"start":null,"finish":null,"elapsedMinutes":0,"breakMinutes":0,"workingMinutes":0}}],
         "summary":{"todayConfirmed":["j1"],"upcoming":["j2"],"inProgress":["j1"],"completed":[],"awaitingSignoff":[],"unconfirmed":["j2"],"conflicts":[["j1","j9"]]},
         "calendar":{"connected":true,"lastSyncedAt":null,"lastError":null}}
    """.trimIndent()

    @Test
    fun `parses jobs without inventing missing values`() {
        val snap = JobsApi.parseSnapshot(payload)
        assertEquals(2, snap.jobs.size)
        val j1 = snap.jobs[0]
        assertEquals(JobState.IN_PROGRESS, j1.state)
        assertEquals(Instant.parse("2026-11-24T22:30:00Z"), j1.scheduledStart)
        assertEquals("0412 000 000", j1.customerPhone)
        assertEquals(25, j1.workingMinutes)
        val j2 = snap.jobs[1]
        assertTrue(j2.dateOnly); assertNull(j2.scheduledStart); assertNull(j2.pickup); assertNull(j2.customerPhone)
        assertEquals(BookingStatus.UNKNOWN, j2.bookingStatus)
        assertEquals(setOf("j2"), snap.unconfirmed)
        assertEquals(listOf("j1" to "j9"), snap.conflicts)
        assertTrue(snap.calendarConnected)
    }

    private fun job(id: String = "j", start: String = "2026-11-24T22:30:00Z", state: JobState = JobState.SCHEDULED,
                    cancelled: Boolean = false, dateOnly: Boolean = false, end: String? = "2026-11-25T02:30:00Z") = Job(
        id = id, title = "t", scheduledStart = if (dateOnly) null else Instant.parse(start),
        scheduledEnd = if (dateOnly || end == null) null else Instant.parse(end), dateOnly = dateOnly, serviceDate = null,
        bookingStatus = BookingStatus.CONFIRMED, cancelled = cancelled, state = state, signoff = SignoffStatus.NONE,
        reference = null, customerName = null, customerPhone = null, pickup = null, destination = null, truck = null,
        crewSize = null, notes = null, assignees = emptyList(), actualStart = null, actualFinish = null,
        elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0
    )

    private val allOn = NotificationCategory.entries.associateWith { true }

    @Test
    fun `plans time based reminders only in the future and respects toggles`() {
        val now = Instant.parse("2026-11-23T10:00:00Z")
        val plan = ReminderPlanner.plan(listOf(job()), allOn, now)
        assertEquals(
            listOf(
                NotificationCategory.UPCOMING_24H, NotificationCategory.UPCOMING_1H,
                NotificationCategory.STARTING_SOON_15M, NotificationCategory.SCHEDULED_START
            ),
            plan.map { it.category }
        )
        assertEquals(Instant.parse("2026-11-23T22:30:00Z"), plan[0].at)
        val late = ReminderPlanner.plan(listOf(job()), allOn, Instant.parse("2026-11-24T22:10:00Z"))
        assertEquals(listOf(NotificationCategory.STARTING_SOON_15M, NotificationCategory.SCHEDULED_START), late.map { it.category })
        val off = ReminderPlanner.plan(listOf(job()), allOn + (NotificationCategory.UPCOMING_1H to false), now)
        assertFalse(off.any { it.category == NotificationCategory.UPCOMING_1H })
    }

    @Test
    fun `no reminders for cancelled date-only or completed jobs, running-late only while in progress`() {
        val now = Instant.parse("2026-11-24T10:00:00Z")
        assertTrue(ReminderPlanner.plan(listOf(job(cancelled = true), job(id = "d", dateOnly = true), job(id = "c", state = JobState.COMPLETED)), allOn, now).isEmpty())
        val running = ReminderPlanner.plan(listOf(job(state = JobState.IN_PROGRESS)), allOn, now)
        assertEquals(listOf(NotificationCategory.RUNNING_LATE), running.map { it.category })
        assertEquals(Instant.parse("2026-11-25T02:30:00Z"), running[0].at)
    }

    @Test
    fun `reminder keys are stable so re-syncs and reboots cannot duplicate them, and change when rescheduled`() {
        val now = Instant.parse("2026-11-24T10:00:00Z")
        val a = ReminderPlanner.plan(listOf(job()), allOn, now).map { it.key }
        val b = ReminderPlanner.plan(listOf(job()), allOn, now.plusSeconds(60)).map { it.key }
        assertEquals(a, b)
        val moved = ReminderPlanner.plan(listOf(job(start = "2026-11-25T22:30:00Z", end = "2026-11-26T02:30:00Z")), allOn, now).map { it.key }
        assertTrue(a.intersect(moved.toSet()).isEmpty())
    }

    @Test
    fun `change detector reports reschedule, cancellation, completion, sign-off pending and assignment changes once`() {
        val old = mapOf("j" to JobFingerprint.of(job()))
        assertTrue(ChangeDetector.diff(old, listOf(job())).isEmpty())
        assertEquals(listOf(NotificationCategory.RESCHEDULED_OR_CANCELLED), ChangeDetector.diff(old, listOf(job(start = "2026-11-26T00:00:00Z"))).map { it.category })
        assertEquals(listOf(NotificationCategory.RESCHEDULED_OR_CANCELLED), ChangeDetector.diff(old, listOf(job(cancelled = true))).map { it.category })
        val done = ChangeDetector.diff(mapOf("j" to JobFingerprint.of(job(state = JobState.IN_PROGRESS))), listOf(job(state = JobState.COMPLETED)))
        assertEquals(listOf(NotificationCategory.COMPLETED, NotificationCategory.SIGNOFF_PENDING), done.map { it.category })
        assertEquals(listOf(NotificationCategory.ASSIGNMENT_CHANGED), ChangeDetector.diff(old, listOf(job().copy(assignees = listOf("sam")))).map { it.category })
        assertTrue(ChangeDetector.diff(emptyMap(), listOf(job())).isEmpty()) // first sync never spams
    }

    @Test
    fun `notification text never contains phone or address`() {
        val j = job().copy(customerPhone = "0412 000 000", pickup = "1 A St", customerName = "Jane", title = "Removal - Jane")
        val publicText = ReminderText.publicVersion(NotificationCategory.UPCOMING_1H)
        val privateText = ReminderText.body(NotificationCategory.UPCOMING_1H, j)
        listOf(publicText, privateText).forEach {
            assertFalse(it.contains("0412")); assertFalse(it.contains("1 A St"))
        }
        assertFalse(publicText.contains("Jane"))
    }
}
