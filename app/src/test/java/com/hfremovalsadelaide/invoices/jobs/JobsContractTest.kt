package com.hfremovalsadelaide.invoices.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Contract test: jobs_contract.json is the real output of the backend's listJobs() (run against PostgreSQL with
 * synthetic bookings), so a change to the API shape breaks this test instead of the live app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JobsContractTest {
    private val snapshot = JobsApi.parseSnapshot(
        requireNotNull(javaClass.classLoader?.getResourceAsStream("jobs_contract.json")).bufferedReader().readText()
    )

    @Test
    fun `backend payload parses with every field the app relies on`() {
        assertEquals(3, snapshot.jobs.size)
        val done = snapshot.jobs.first { it.title.startsWith("Removal") }
        assertEquals(JobState.COMPLETED, done.state)
        assertEquals(BookingStatus.CONFIRMED, done.bookingStatus)
        assertEquals(Instant.parse("2026-11-22T22:30:00Z"), done.scheduledStart)
        assertEquals("HF-TEST-1", done.reference)
        assertEquals("0400 000 000", done.customerPhone)
        assertEquals("1 Test St, Adelaide SA", done.pickup)
        assertEquals("MR Truck · 12 tonne", done.truck)
        assertEquals(2, done.crewSize)
        assertNotNull(done.actualStart)
        assertNotNull(done.actualFinish)
        assertEquals(SignoffStatus.NONE, done.signoff)
        assertTrue(done.id in snapshot.awaitingSignoff)
    }

    @Test
    fun `date-only and unconfirmed bookings are never presented as confirmed or timed`() {
        val marker = snapshot.jobs.first { it.title == "Dispatch marker" }
        assertTrue(marker.dateOnly)
        assertNull(marker.scheduledStart)
        assertEquals("2026-11-24", marker.serviceDate)
        assertEquals(BookingStatus.UNKNOWN, marker.bookingStatus)
        val tbc = snapshot.jobs.first { it.title == "Second job" }
        assertEquals(BookingStatus.UNCONFIRMED, tbc.bookingStatus)
        assertTrue(marker.id in snapshot.unconfirmed && tbc.id in snapshot.unconfirmed)
        assertTrue(snapshot.calendarConnected)
    }
}
