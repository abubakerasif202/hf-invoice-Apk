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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookingLifecycleTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun job(
        state: JobState = JobState.SCHEDULED,
        cancelled: Boolean = false,
        signoff: SignoffStatus = SignoffStatus.NONE,
        lifecycle: BookingLifecycle? = null
    ) = Job(
        id = "j1", title = "Move", scheduledStart = Instant.parse("2026-11-28T22:30:00Z"), scheduledEnd = Instant.parse("2026-11-29T02:30:00Z"),
        dateOnly = false, serviceDate = null, bookingStatus = BookingStatus.CONFIRMED, cancelled = cancelled, state = state, signoff = signoff,
        reference = null, customerName = "Jane", customerPhone = null, pickup = null, destination = null, truck = null, crewSize = null,
        notes = null, assignees = emptyList(), actualStart = null, actualFinish = null, elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0,
        lifecycle = lifecycle
    )

    private fun lifecycleJson(status: String, label: String, reason: String? = null) =
        JSONObject().put("status", status).put("label", label).put("cancellationReason", reason ?: JSONObject.NULL)

    private fun serverJob(lifecycle: JSONObject?) = JSONObject()
        .put("id", "j1").put("title", "Move").put("bookingStatus", "confirmed").put("state", "scheduled")
        .put("signoffStatus", "none").put("details", JSONObject()).put("actual", JSONObject())
        .apply { if (lifecycle != null) put("lifecycle", lifecycle) }

    @Test
    fun `the server lifecycle is parsed alongside the unchanged calendar booking flag`() {
        val parsed = JobsApi.parseJob(serverJob(lifecycleJson("cancelled", "Cancelled", "Customer postponed")))
        assertEquals(BookingLifecycle(LifecycleStatus.CANCELLED, "Cancelled", "Customer postponed"), parsed.lifecycle)
        assertEquals(BookingStatus.CONFIRMED, parsed.bookingStatus)
    }

    /** job_detail_moving_day_contract.json is real backend jobDetail() output (tests/moving-day.test.ts). */
    @Test
    fun `the real backend job detail carries an in-progress lifecycle`() {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream("job_detail_moving_day_contract.json")).bufferedReader().readText()
        val job = JobsApi.parseJob(JSONObject(text))
        assertEquals(BookingLifecycle(LifecycleStatus.IN_PROGRESS, "In progress", null), job.lifecycle)
        assertEquals(JobStatusPill("In progress", PillTone.ACTIVE), JobStatusPill.forJob(job))
    }

    @Test
    fun `a job without lifecycle (worker view or older backend) or with an unknown status has none`() {
        assertNull(JobsApi.parseJob(serverJob(null)).lifecycle)
        assertNull(JobsApi.parseJob(serverJob(lifecycleJson("teleported", "Teleported"))).lifecycle)
    }

    @Test
    fun `every server status maps to a lifecycle status`() {
        val server = listOf(
            "new_enquiry", "quotation_required", "quote_sent", "awaiting_confirmation", "confirmed", "assigned",
            "travelling", "in_progress", "completed", "awaiting_invoice", "invoiced", "paid", "cancelled"
        )
        assertEquals(server, LifecycleStatus.entries.map { it.server })
    }

    @Test
    fun `the status pill shows the lifecycle label with a tone for its stage`() {
        assertEquals(JobStatusPill("Paid", PillTone.SUCCESS), JobStatusPill.forJob(job(lifecycle = BookingLifecycle(LifecycleStatus.PAID, "Paid", null))))
        assertEquals(JobStatusPill("Cancelled", PillTone.DANGER), JobStatusPill.forJob(job(lifecycle = BookingLifecycle(LifecycleStatus.CANCELLED, "Cancelled", "x"))))
        assertEquals(JobStatusPill("Travelling", PillTone.ACTIVE), JobStatusPill.forJob(job(lifecycle = BookingLifecycle(LifecycleStatus.TRAVELLING, "Travelling", null))))
        assertEquals(JobStatusPill("Awaiting confirmation", PillTone.NEUTRAL), JobStatusPill.forJob(job(lifecycle = BookingLifecycle(LifecycleStatus.AWAITING_CONFIRMATION, "Awaiting confirmation", null))))
        assertEquals(
            "a paused job keeps showing that it is paused",
            JobStatusPill("In progress · paused", PillTone.ACTIVE),
            JobStatusPill.forJob(job(state = JobState.PAUSED, lifecycle = BookingLifecycle(LifecycleStatus.IN_PROGRESS, "In progress", null)))
        )
    }

    @Test
    fun `without a lifecycle the pill keeps its previous wording`() {
        assertEquals(JobStatusPill("Scheduled", PillTone.NEUTRAL), JobStatusPill.forJob(job()))
        assertEquals(JobStatusPill("Cancelled", PillTone.DANGER), JobStatusPill.forJob(job(cancelled = true)))
        assertEquals(JobStatusPill("Paused", PillTone.SECONDARY), JobStatusPill.forJob(job(state = JobState.PAUSED)))
        assertEquals(JobStatusPill("Signed", PillTone.SUCCESS), JobStatusPill.forJob(job(state = JobState.COMPLETED, signoff = SignoffStatus.SIGNED)))
        assertEquals(JobStatusPill("Awaiting sign-off", PillTone.PRIMARY), JobStatusPill.forJob(job(state = JobState.COMPLETED)))
    }

    @Test
    fun `the offline snapshot cache keeps the lifecycle`() {
        val lifecycle = BookingLifecycle(LifecycleStatus.CANCELLED, "Cancelled", "Date clash")
        val snapshot = JobsSnapshot(listOf(job(lifecycle = lifecycle)), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null)
        assertEquals(lifecycle, JobSnapshotCodec.decode(JobSnapshotCodec.encode(snapshot))?.jobs?.single()?.lifecycle)
        val without = JobsSnapshot(listOf(job()), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null)
        assertNull(JobSnapshotCodec.decode(JobSnapshotCodec.encode(without))?.jobs?.single()?.lifecycle)
    }

    @Test
    fun `only a scheduled, not yet cancelled booking offers Cancel, and only with permission`() {
        assertTrue(BookingCancel.canOffer(job(), hasPermission = true))
        assertEquals(false, BookingCancel.canOffer(job(), hasPermission = false))
        assertEquals(false, BookingCancel.canOffer(job(state = JobState.IN_PROGRESS), hasPermission = true))
        assertEquals(false, BookingCancel.canOffer(job(cancelled = true), hasPermission = true))
        assertEquals(false, BookingCancel.canOffer(job(lifecycle = BookingLifecycle(LifecycleStatus.CANCELLED, "Cancelled", "x")), hasPermission = true))
    }

    private class Fake : JobsGateway {
        val calls = mutableListOf<String>()
        var cancelResult: JobsResult<String> = JobsResult.Success("{}")
        override suspend fun snapshot(): JobsResult<JobsSnapshot> {
            calls += "snapshot"
            return JobsResult.Success(JobsSnapshot(emptyList(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null))
        }
        override suspend fun detail(id: String): JobsResult<Job> = JobsResult.Failure("n/a")
        override suspend fun sync(): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun act(id: String, action: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun skipSignoff(id: String, reason: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun invoiceDraft(id: String): JobsResult<JSONObject> = JobsResult.Failure("n/a")
        override suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>> = JobsResult.Success(emptyMap())
        override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun cancelBooking(id: String, reason: String): JobsResult<String> { calls += "cancel:$id:$reason"; return cancelResult }
    }

    @Test
    fun `cancelling sends the trimmed reason, confirms, and refreshes`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f, onSnapshot = {})
        v.cancelBooking("j1", "  Customer postponed  "); advanceUntilIdle()
        assertEquals(listOf("cancel:j1:Customer postponed", "snapshot"), f.calls)
        assertEquals("Booking cancelled.", v.state.value.message)
    }

    @Test
    fun `a blank reason is refused locally and nothing is sent`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f, onSnapshot = {})
        v.cancelBooking("j1", "   "); advanceUntilIdle()
        assertTrue(f.calls.isEmpty())
        assertNotNull(v.state.value.message)
    }

    @Test
    fun `a server refusal is shown and nothing is refreshed`() = runTest(dispatcher) {
        val f = Fake().apply { cancelResult = JobsResult.Failure("Only a booking that has not started can be cancelled.") }
        val v = JobsViewModel(f, onSnapshot = {})
        v.cancelBooking("j1", "Too late"); advanceUntilIdle()
        assertEquals(listOf("cancel:j1:Too late"), f.calls)
        assertEquals("Only a booking that has not started can be cancelled.", v.state.value.message)
    }
}
