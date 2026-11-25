package com.hfremovalsadelaide.invoices.jobs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Mirrors the backend's 409 body (lib/jobs/http.ts fail() for a ScheduleConflictError). */
private const val CONFLICT_BODY = """
{
  "error": "Scheduling conflict.", "code": "schedule_conflict", "overrideAllowed": true,
  "conflicts": [
    {"kind": "crew", "subject": "Sam", "otherJobId": "j2", "otherJobTitle": "Glenelg move"},
    {"kind": "staff_unavailable", "subject": "Kim", "from": "2026-11-26", "to": "2026-11-27", "reason": "leave 2026-11-26 to 2026-11-27: family"},
    {"kind": "vehicle_status", "subject": "T9", "reason": "T9 is marked maintenance"}
  ]
}
"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleConflictOverrideTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the backend 409 body parses into conflicts`() {
        val conflicts = parseScheduleConflicts(CONFLICT_BODY)
        assertEquals(3, conflicts.size)
        assertEquals("crew", conflicts[0].kind)
        assertEquals("Glenelg move", conflicts[0].otherJobTitle)
        assertEquals("2026-11-26", conflicts[1].from)
    }

    @Test
    fun `other error bodies and non-conflict 409s are not treated as scheduling conflicts`() {
        assertTrue(parseScheduleConflicts("""{"error": "Google Calendar is not connected."}""").isEmpty())
        assertTrue(parseScheduleConflicts("not json").isEmpty())
        assertTrue(parseScheduleConflicts("""{"code": "schedule_conflict", "conflicts": []}""").isEmpty())
    }

    @Test
    fun `each conflict reads as a plain sentence for the dispatcher`() {
        val c = parseScheduleConflicts(CONFLICT_BODY)
        assertEquals("Sam is already on \"Glenelg move\" at the same time.", describeConflict(c[0]))
        assertEquals("Kim is unavailable: leave 2026-11-26 to 2026-11-27: family", describeConflict(c[1]))
        assertEquals("T9 is marked maintenance", describeConflict(c[2]))
    }

    private fun job() = Job(
        id = "j1", title = "Move", scheduledStart = Instant.parse("2026-11-24T22:30:00Z"), scheduledEnd = Instant.parse("2026-11-25T02:30:00Z"),
        dateOnly = false, serviceDate = null, bookingStatus = BookingStatus.CONFIRMED, cancelled = false, state = JobState.SCHEDULED,
        signoff = SignoffStatus.NONE, reference = null, customerName = "Jane", customerPhone = null, pickup = null, destination = null,
        truck = null, crewSize = null, notes = null, assignees = emptyList(), actualStart = null, actualFinish = null,
        elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0
    )

    private class Fake : JobsGateway {
        val overrideReasons = mutableListOf<String?>()
        var refuseUnlessOverridden = true
        private val snap = JobsSnapshot(emptyList(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null)
        override suspend fun snapshot(): JobsResult<JobsSnapshot> = JobsResult.Success(snap)
        override suspend fun detail(id: String): JobsResult<Job> = JobsResult.Failure("n/a")
        override suspend fun sync(): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun act(id: String, action: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun skipSignoff(id: String, reason: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun invoiceDraft(id: String): JobsResult<JSONObject> = JobsResult.Failure("none")
        override suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>> = JobsResult.Success(emptyMap())
        override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?): JobsResult<String> = JobsResult.Success("{}")

        private fun result(reason: String?): JobsResult<String> =
            if (refuseUnlessOverridden && reason == null) {
                JobsResult.Failure("Scheduling conflict.", conflicts = parseScheduleConflicts(CONFLICT_BODY))
            } else {
                JobsResult.Success("{}")
            }

        override suspend fun assignJob(
            id: String, staffAssignments: List<Triple<String?, String, String>>, vehicleId: String?, truck: String?,
            travelNotes: String, dispatchNotes: String, overrideReason: String?
        ): JobsResult<String> { overrideReasons += overrideReason; return result(overrideReason) }

        override suspend fun rescheduleJob(id: String, startIso: String, endIso: String, reason: String, overrideReason: String?): JobsResult<String> {
            overrideReasons += overrideReason
            return result(overrideReason)
        }
    }

    @Test
    fun `a refused assignment raises a conflict prompt instead of a generic error`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.assignJob("j1", emptyList(), null, null, "", ""); advanceUntilIdle()
        val prompt = v.state.value.scheduleConflict
        assertNotNull(prompt)
        assertEquals(3, prompt!!.conflicts.size)
        assertNull(v.state.value.message) // not shown as an error message
    }

    @Test
    fun `confirming with a reason retries the same assignment with that reason and clears the prompt`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.assignJob("j1", emptyList(), null, null, "", ""); advanceUntilIdle()
        v.confirmScheduleOverride("Sam finishes the other move early"); advanceUntilIdle()
        assertEquals(listOf<String?>(null, "Sam finishes the other move early"), f.overrideReasons)
        assertNull(v.state.value.scheduleConflict)
    }

    @Test
    fun `a missing or too-short reason is rejected locally and nothing is resent`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.assignJob("j1", emptyList(), null, null, "", ""); advanceUntilIdle()
        v.confirmScheduleOverride("no"); advanceUntilIdle()
        assertEquals(1, f.overrideReasons.size)
        assertNotNull(v.state.value.scheduleConflict) // prompt stays so the dispatcher can fix the reason
        assertNotNull(v.state.value.message)
    }

    @Test
    fun `dismissing the prompt sends nothing`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.assignJob("j1", emptyList(), null, null, "", ""); advanceUntilIdle()
        v.dismissScheduleConflict()
        assertNull(v.state.value.scheduleConflict)
        assertEquals(1, f.overrideReasons.size)
    }

    @Test
    fun `a refused reschedule can be overridden the same way`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.rescheduleJob("j1", "2026-11-25T01:00:00Z", "2026-11-25T03:00:00Z", "Customer request"); advanceUntilIdle()
        assertNotNull(v.state.value.scheduleConflict)
        v.confirmScheduleOverride("Customer asked for the earlier slot"); advanceUntilIdle()
        assertEquals(listOf<String?>(null, "Customer asked for the earlier slot"), f.overrideReasons)
    }
}
