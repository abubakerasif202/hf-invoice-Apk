package com.hfremovalsadelaide.invoices.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DispatchLogicTest {
    // Reference clock: 09:30 Adelaide on 2026-11-24.
    private val now = Instant.parse("2026-11-23T23:00:00Z")

    private fun job(
        id: String,
        start: String? = "2026-11-24T00:00:00Z",
        end: String? = "2026-11-24T04:00:00Z",
        truck: String? = null,
        vehicleId: String? = null,
        assignees: List<String> = emptyList(),
        staffIds: List<String> = emptyList(),
        state: JobState = JobState.SCHEDULED,
        booking: BookingStatus = BookingStatus.CONFIRMED,
        cancelled: Boolean = false,
        notice: Boolean = false,
        dateOnly: Boolean = false,
        missing: List<String> = emptyList()
    ) = Job(
        id = id, title = "Job $id", scheduledStart = start?.let(Instant::parse), scheduledEnd = end?.let(Instant::parse),
        dateOnly = dateOnly, serviceDate = null, bookingStatus = booking, cancelled = cancelled, state = state,
        signoff = SignoffStatus.NONE, reference = null, customerName = null, customerPhone = null, pickup = null,
        destination = null, truck = truck, crewSize = null, notes = null, assignees = assignees, actualStart = null,
        actualFinish = null, elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0, notice = notice,
        vehicleId = vehicleId, staffIds = staffIds, missingInfo = missing
    )

    @Test
    fun `overlap needs real times that intersect and ignores date-only jobs`() {
        val a = job("a")
        assertTrue(DispatchLogic.overlaps(a, job("b", "2026-11-24T03:00:00Z", "2026-11-24T06:00:00Z")))
        assertFalse(DispatchLogic.overlaps(a, job("c", "2026-11-24T04:00:00Z", "2026-11-24T06:00:00Z"))) // touching, not overlapping
        assertFalse(DispatchLogic.overlaps(a, job("d", start = null, end = null, dateOnly = true)))
    }

    @Test
    fun `clashes flag a staff member already booked on an overlapping job`() {
        val busy = job("busy", staffIds = listOf("s1"))
        val clashes = DispatchLogic.clashes(job("target"), listOf(busy), setOf("s1", "s2"), emptySet(), null, null)
        val c = clashes.single()
        assertEquals(ClashKind.CREW, c.kind)
        assertEquals("busy", c.otherJobId)
    }

    @Test
    fun `crew names match case-insensitively for manually entered crew`() {
        val busy = job("busy", assignees = listOf("Sam Lee"))
        val clashes = DispatchLogic.clashes(job("target"), listOf(busy), emptySet(), setOf("  sam lee "), null, null)
        assertEquals(ClashKind.CREW, clashes.single().kind)
        assertEquals("sam lee", clashes.single().label.lowercase().trim())
    }

    @Test
    fun `truck clash prefers vehicle id and falls back to the truck text`() {
        val byId = job("byId", vehicleId = "v1")
        val byText = job("byText", truck = "MR 12t")
        val result = DispatchLogic.clashes(job("target"), listOf(byId, byText), emptySet(), emptySet(), "v1", "mr 12T")
        assertEquals(setOf("byId", "byText"), result.map { it.otherJobId }.toSet())
        assertTrue(result.all { it.kind == ClashKind.TRUCK })
    }

    @Test
    fun `clashes ignore itself cancelled notices completed jobs and non-overlapping jobs`() {
        val others = listOf(
            job("target", staffIds = listOf("s1")),
            job("gone", staffIds = listOf("s1"), cancelled = true),
            job("note", staffIds = listOf("s1"), notice = true),
            job("done", staffIds = listOf("s1"), state = JobState.COMPLETED),
            job("later", staffIds = listOf("s1"), start = "2026-11-24T05:00:00Z", end = "2026-11-24T08:00:00Z")
        )
        assertTrue(DispatchLogic.clashes(job("target"), others, setOf("s1"), emptySet(), null, null).isEmpty())
    }

    @Test
    fun `attention is high for a conflict or an imminent job with no crew or truck`() {
        val conflict = DispatchLogic.attention(job("a", truck = "T", assignees = listOf("x")), setOf("a"), now)
        assertEquals(AttentionLevel.HIGH, conflict.level)

        val imminent = DispatchLogic.attention(job("b", start = "2026-11-24T05:00:00Z"), emptySet(), now) // starts in ~6h
        assertEquals(AttentionLevel.HIGH, imminent.level)
        assertTrue(imminent.reasons.any { it.contains("crew") })
        assertTrue(imminent.reasons.any { it.contains("truck") })
    }

    @Test
    fun `attention is medium for later jobs that are unassigned unconfirmed or missing details`() {
        val farOff = job("c", start = "2026-11-27T00:00:00Z", end = "2026-11-27T04:00:00Z")
        assertEquals(AttentionLevel.MEDIUM, DispatchLogic.attention(farOff, emptySet(), now).level)

        val ready = job("d", truck = "T", assignees = listOf("x"), start = "2026-11-27T00:00:00Z", end = "2026-11-27T04:00:00Z")
        assertEquals(AttentionLevel.NONE, DispatchLogic.attention(ready, emptySet(), now).level)

        val unconfirmed = ready.copy(bookingStatus = BookingStatus.UNCONFIRMED)
        assertEquals(AttentionLevel.MEDIUM, DispatchLogic.attention(unconfirmed, emptySet(), now).level)

        val missing = ready.copy(missingInfo = listOf("Customer phone"))
        assertEquals(AttentionLevel.MEDIUM, DispatchLogic.attention(missing, emptySet(), now).level)
    }

    @Test
    fun `attention is none for cancelled notices and completed or in-progress jobs`() {
        listOf(
            job("x", cancelled = true),
            job("y", notice = true),
            job("z", state = JobState.COMPLETED),
            job("w", state = JobState.IN_PROGRESS)
        ).forEach { assertEquals(AttentionLevel.NONE, DispatchLogic.attention(it, setOf(it.id), now).level) }
    }
}
