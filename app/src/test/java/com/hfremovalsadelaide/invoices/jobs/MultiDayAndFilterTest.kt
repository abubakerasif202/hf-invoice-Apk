package com.hfremovalsadelaide.invoices.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MultiDayAndFilterTest {
    private fun job(
        id: String = "j",
        start: String? = "2026-11-24T22:30:00Z", // 09:00 on 25 Nov Adelaide
        end: String? = "2026-11-25T02:30:00Z",
        appStart: String? = null,
        appEnd: String? = null,
        dateOnly: Boolean = false,
        serviceDate: String? = null,
        truck: String? = null,
        assignees: List<String> = emptyList(),
        staffIds: List<String> = emptyList(),
        details: List<JobAssignment>? = null,
        priority: JobPriority = JobPriority.NORMAL
    ) = Job(
        id = id, title = "Job $id", scheduledStart = start?.let(Instant::parse), scheduledEnd = end?.let(Instant::parse),
        dateOnly = dateOnly, serviceDate = serviceDate, bookingStatus = BookingStatus.CONFIRMED, cancelled = false,
        state = JobState.SCHEDULED, signoff = SignoffStatus.NONE, reference = null, customerName = null, customerPhone = null,
        pickup = null, destination = null, truck = truck, crewSize = null, notes = null, assignees = assignees,
        actualStart = null, actualFinish = null, elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0,
        appScheduledStart = appStart?.let(Instant::parse), appScheduledEnd = appEnd?.let(Instant::parse),
        staffIds = staffIds, assignmentDetails = details, priority = priority
    )

    private val d25 = LocalDate.of(2026, 11, 25)

    @Test
    fun `an in-app reschedule moves the job to its new Adelaide day`() {
        val moved = job(appStart = "2026-11-26T22:30:00Z", appEnd = "2026-11-27T02:30:00Z") // 27 Nov, Adelaide
        assertEquals(LocalDate.of(2026, 11, 27), JobFormat.localDate(moved))
    }

    @Test
    fun `a single-day job covers only its own day`() {
        assertEquals(listOf(d25), JobFormat.datesCovered(job()))
    }

    @Test
    fun `a multi-day job covers every Adelaide day it spans`() {
        val long = job(start = "2026-11-24T22:30:00Z", end = "2026-11-27T05:00:00Z") // 25 Nov 09:00 to 27 Nov 15:30
        assertEquals(listOf(d25, d25.plusDays(1), d25.plusDays(2)), JobFormat.datesCovered(long))
        assertTrue(JobFormat.coversDate(long, d25.plusDays(1)))
        assertFalse(JobFormat.coversDate(long, d25.plusDays(3)))
    }

    @Test
    fun `a job ending exactly at midnight does not occupy the next day`() {
        val endsMidnight = job(start = "2026-11-25T00:00:00Z", end = "2026-11-25T13:30:00Z") // ends 00:00 on 26 Nov Adelaide
        assertEquals(listOf(d25), JobFormat.datesCovered(endsMidnight))
    }

    @Test
    fun `date-only and untimed jobs use their service date only`() {
        assertEquals(listOf(d25), JobFormat.datesCovered(job(start = null, end = null, dateOnly = true, serviceDate = "2026-11-25")))
        assertTrue(JobFormat.datesCovered(job(start = null, end = null, serviceDate = null)).isEmpty())
    }

    @Test
    fun `an absurdly long span is capped so the board cannot hang`() {
        val huge = job(start = "2026-01-01T00:00:00Z", end = "2030-01-01T00:00:00Z")
        assertEquals(JobFormat.MAX_SPAN_DAYS, JobFormat.datesCovered(huge).size)
    }

    @Test
    fun `day position tells the crew which day of a multi-day job it is`() {
        val long = job(start = "2026-11-24T22:30:00Z", end = "2026-11-27T05:00:00Z")
        assertEquals(2 to 3, JobFormat.dayPosition(long, d25.plusDays(1)))
        assertNull(JobFormat.dayPosition(job(), d25))
    }

    @Test
    fun `priority parses from the server value and defaults to normal`() {
        assertEquals(JobPriority.URGENT, JobPriority.from("urgent"))
        assertEquals(JobPriority.NORMAL, JobPriority.from("bogus"))
        assertEquals(JobPriority.NORMAL, JobPriority.from(null))
    }

    private val sub = listOf(JobAssignment("s1", "Sam", "mover"), JobAssignment(null, "Acme Removals", "subcontractor"))

    @Test
    fun `filters narrow the board to unassigned work, one crew member, one truck or subcontractor jobs`() {
        val unassigned = job("u")
        val withCrew = job("c", truck = "T1", assignees = listOf("Sam"), staffIds = listOf("s1"), details = sub.take(1))
        val withSub = job("s", truck = "T2", assignees = listOf("Acme Removals"), details = sub.drop(1))
        val all = listOf(unassigned, withCrew, withSub)

        assertEquals(all, all.filter { DispatchLogic.matches(it, DispatchFilter()) })
        assertEquals(listOf(unassigned), all.filter { DispatchLogic.matches(it, DispatchFilter(unassignedOnly = true)) })
        assertEquals(listOf(withSub), all.filter { DispatchLogic.matches(it, DispatchFilter(subcontractorsOnly = true)) })
        assertEquals(listOf(withCrew), all.filter { DispatchLogic.matches(it, DispatchFilter(crew = "sam")) })
        assertEquals(listOf(withSub), all.filter { DispatchLogic.matches(it, DispatchFilter(truck = "T2")) })
    }

    @Test
    fun `filter choices list each crew member and truck once, sorted`() {
        val jobs = listOf(
            job("a", truck = "T2", assignees = listOf("Sam", "kim")),
            job("b", truck = "T1", assignees = listOf("sam", "Lee"))
        )
        assertEquals(listOf("kim", "Lee", "Sam"), DispatchLogic.crewChoices(jobs))
        assertEquals(listOf("T1", "T2"), DispatchLogic.truckChoices(jobs))
    }

    @Test
    fun `lane order puts urgent and high priority first, then earliest start`() {
        val urgentLate = job("u", start = "2026-11-25T05:00:00Z", end = "2026-11-25T08:00:00Z", priority = JobPriority.URGENT)
        val normalEarly = job("n", start = "2026-11-25T00:00:00Z", end = "2026-11-25T03:00:00Z")
        val highMid = job("h", start = "2026-11-25T02:00:00Z", end = "2026-11-25T05:00:00Z", priority = JobPriority.HIGH)
        assertEquals(listOf("u", "h", "n"), DispatchLogic.laneOrder(listOf(normalEarly, highMid, urgentLate)).map { it.id })
    }
}
