package com.hfremovalsadelaide.invoices.jobs

import org.json.JSONObject
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
class MovingDayTest {
    private val t0 = Instant.parse("2026-11-25T23:00:00Z")

    private fun job(
        state: JobState = JobState.SCHEDULED,
        cancelled: Boolean = false,
        notice: Boolean = false,
        milestones: JobMilestones? = null,
        stops: List<JobStop> = emptyList()
    ) = Job(
        id = "j", title = "Move", scheduledStart = t0, scheduledEnd = t0.plusSeconds(3600 * 4), dateOnly = false, serviceDate = null,
        bookingStatus = BookingStatus.CONFIRMED, cancelled = cancelled, state = state, signoff = SignoffStatus.NONE, reference = null,
        customerName = null, customerPhone = null, pickup = "1 A St", destination = "9 Z Rd", truck = null, crewSize = null, notes = null,
        assignees = emptyList(), actualStart = null, actualFinish = null, elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0,
        notice = notice, milestones = milestones, stops = stops
    )

    private fun stop(seq: Int, kind: StopKind = StopKind.PICKUP, arrived: Instant? = null, done: Instant? = null) =
        JobStop("s$seq", seq, kind, "$seq Main St", "", "", "", arrived, done)

    @Test
    fun `travelling and arrived are only offered before the job starts, once each, and only to crew who can act`() {
        assertTrue(MovingDay.canMarkTravelling(job(), canAct = true))
        assertFalse(MovingDay.canMarkTravelling(job(), canAct = false))
        assertFalse(MovingDay.canMarkTravelling(job(state = JobState.IN_PROGRESS), canAct = true))
        assertFalse(MovingDay.canMarkTravelling(job(cancelled = true), canAct = true))
        assertFalse(MovingDay.canMarkTravelling(job(notice = true), canAct = true))

        val travelling = job(milestones = JobMilestones(t0, null, null))
        assertFalse(MovingDay.canMarkTravelling(travelling, canAct = true))
        assertTrue(MovingDay.canMarkArrived(travelling, canAct = true))
        // Arrival can be recorded even if travelling was forgotten.
        assertTrue(MovingDay.canMarkArrived(job(), canAct = true))
        assertFalse(MovingDay.canMarkArrived(job(milestones = JobMilestones(t0, t0, 5)), canAct = true))
        // Once arrived, "travelling" no longer makes sense.
        assertFalse(MovingDay.canMarkTravelling(job(milestones = JobMilestones(null, t0, null)), canAct = true))
    }

    @Test
    fun `journey status follows the milestones then the work clock`() {
        assertEquals(JourneyStatus.NOT_STARTED, MovingDay.journeyStatus(job()))
        assertEquals(JourneyStatus.TRAVELLING, MovingDay.journeyStatus(job(milestones = JobMilestones(t0, null, null))))
        assertEquals(JourneyStatus.ARRIVED, MovingDay.journeyStatus(job(milestones = JobMilestones(t0, t0, 3))))
        assertEquals(JourneyStatus.WORKING, MovingDay.journeyStatus(job(state = JobState.IN_PROGRESS, milestones = JobMilestones(t0, t0, 3))))
        assertEquals(JourneyStatus.WORKING, MovingDay.journeyStatus(job(state = JobState.PAUSED)))
        assertEquals(JourneyStatus.DONE, MovingDay.journeyStatus(job(state = JobState.COMPLETED)))
    }

    @Test
    fun `stop buttons need a running job, arrive before completing, and happen once`() {
        val running = job(state = JobState.IN_PROGRESS)
        val fresh = stop(1)
        assertTrue(MovingDay.canArriveAtStop(running, fresh, canAct = true))
        assertFalse(MovingDay.canArriveAtStop(job(), fresh, canAct = true)) // not started
        assertFalse(MovingDay.canArriveAtStop(running, fresh, canAct = false))
        assertFalse(MovingDay.canCompleteStop(running, fresh, canAct = true)) // not arrived yet

        val arrived = stop(1, arrived = t0)
        assertFalse(MovingDay.canArriveAtStop(running, arrived, canAct = true))
        assertTrue(MovingDay.canCompleteStop(running, arrived, canAct = true))

        val done = stop(1, arrived = t0, done = t0)
        assertFalse(MovingDay.canCompleteStop(running, done, canAct = true))
        assertEquals(StopStatus.COMPLETED, MovingDay.stopStatus(done))
        assertEquals(StopStatus.ARRIVED, MovingDay.stopStatus(arrived))
        assertEquals(StopStatus.PENDING, MovingDay.stopStatus(fresh))
    }

    @Test
    fun `the next stop is the first one not completed`() {
        val stops = listOf(stop(1, arrived = t0, done = t0), stop(2, arrived = t0), stop(3))
        assertEquals("s2", MovingDay.nextStop(job(stops = stops))?.id)
        assertNull(MovingDay.nextStop(job(stops = stops.map { it.copy(arrivedAt = t0, completedAt = t0) })))
        assertNull(MovingDay.nextStop(job()))
    }

    @Test
    fun `the stop summary counts pickups and deliveries and is silent for a simple job`() {
        assertNull(MovingDay.summary(job()))
        assertNull(MovingDay.summary(job(stops = listOf(stop(1)))))
        val multi = job(stops = listOf(stop(1), stop(2), stop(3, StopKind.DELIVERY)))
        assertEquals("2 pickups · 1 delivery", MovingDay.summary(multi))
    }

    private fun draft(address: String, kind: StopKind = StopKind.PICKUP, id: String? = null, visited: Boolean = false) =
        StopDraft(id, kind, address, visited = visited)

    @Test
    fun `the editor adds, reorders and removes stops without touching visited ones`() {
        val list = listOf(draft("A", id = "a", visited = true), draft("B", id = "b"), draft("C", kind = StopKind.DELIVERY, id = "c"))
        assertEquals(listOf("A", "C", "B"), MovingDay.moveDown(list, 1).map { it.address })
        assertEquals(list, MovingDay.moveUp(list, 1)) // cannot go above a stop that was already visited
        assertEquals(listOf("A", "B", "C"), MovingDay.moveUp(MovingDay.moveDown(list, 1), 2).map { it.address }) // down then up is a round trip
        assertEquals(list, MovingDay.moveUp(list, 0)) // already first
        assertEquals(list, MovingDay.moveDown(list, 2)) // already last
        assertEquals(list, MovingDay.remove(list, 0)) // visited stops stay
        assertEquals(listOf("A", "C"), MovingDay.remove(list, 1).map { it.address })
        assertEquals(4, MovingDay.add(list, StopKind.DELIVERY).size)
    }

    @Test
    fun `the editor allows at most 20 stops and requires an address on each`() {
        val full = List(MovingDay.MAX_STOPS) { draft("S$it") }
        assertEquals(MovingDay.MAX_STOPS, MovingDay.add(full, StopKind.PICKUP).size)
        assertNull(MovingDay.validate(listOf(draft("1 A St"), draft("9 Z Rd", StopKind.DELIVERY))))
        assertEquals("Each stop needs an address.", MovingDay.validate(listOf(draft("1 A St"), draft("   "))))
    }

    @Test
    fun `the detail payload parses milestones and stops`() {
        val json = JSONObject(
            """
            {"id":"j1","title":"Move","scheduledStart":"2026-11-25T23:00:00.000Z","scheduledEnd":"2026-11-26T03:00:00.000Z","dateOnly":false,
             "bookingStatus":"confirmed","cancelled":false,"state":"scheduled","signoffStatus":"none","assignees":[],"staffIds":[],
             "actual":{"start":null,"finish":null,"elapsedMinutes":0,"breakMinutes":0,"workingMinutes":0},
             "milestones":{"travellingAt":"2026-11-25T22:00:00.000Z","arrivedAt":"2026-11-25T22:40:00.000Z","travelMinutes":40},
             "stops":[
               {"id":"s1","seq":1,"kind":"pickup","address":"1 A St","contactName":"Jo","contactPhone":"0400","notes":"Lift","arrivedAt":null,"completedAt":null},
               {"id":"s2","seq":2,"kind":"delivery","address":"9 Z Rd","contactName":"","contactPhone":"","notes":"","arrivedAt":null,"completedAt":null}
             ]}
            """.trimIndent()
        )
        val job = JobsApi.parseJob(json)
        assertEquals(40, job.milestones?.travelMinutes)
        assertEquals(Instant.parse("2026-11-25T22:00:00Z"), job.milestones?.travellingAt)
        assertEquals(listOf("1 A St", "9 Z Rd"), job.stops.map { it.address })
        assertEquals(StopKind.DELIVERY, job.stops[1].kind)
        assertEquals("Lift", job.stops[0].notes)
    }

    /** job_detail_moving_day_contract.json is real output of the backend's jobDetail() (synthetic data, tests/moving-day.test.ts). */
    @Test
    fun `the real backend job detail parses with milestones, stops and visit times`() {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream("job_detail_moving_day_contract.json")).bufferedReader().readText()
        val job = JobsApi.parseJob(JSONObject(text))
        assertEquals(JobState.IN_PROGRESS, job.state)
        assertTrue(job.milestones?.travellingAt != null && job.milestones?.arrivedAt != null)
        assertTrue(job.milestones?.travelMinutes != null)
        assertEquals(listOf(StopKind.PICKUP, StopKind.DELIVERY), job.stops.map { it.kind })
        assertEquals(listOf("1 Test St", "9 Sample Rd"), job.stops.map { it.address })
        assertEquals("Lift booked", job.stops[0].notes)
        assertEquals("Jo", job.stops[0].contactName)
        assertTrue(job.stops[0].arrivedAt != null && job.stops[0].completedAt == null)
        assertNull(job.stops[1].arrivedAt)
        assertEquals(JourneyStatus.WORKING, MovingDay.journeyStatus(job))
        assertEquals("2nd stop is next after the first is only arrived at", job.stops[0].id, MovingDay.nextStop(job)?.id)
    }

    @Test
    fun `an older backend without milestones or stops parses as no milestones and no stops`() {
        val job = JobsApi.parseJob(
            JSONObject(
                """{"id":"j1","title":"Move","dateOnly":true,"bookingStatus":"confirmed","cancelled":false,"state":"scheduled","signoffStatus":"none",
                 "assignees":[],"staffIds":[],"actual":{"elapsedMinutes":0,"breakMinutes":0,"workingMinutes":0}}"""
            )
        )
        assertNull(job.milestones)
        assertTrue(job.stops.isEmpty())
    }
}
