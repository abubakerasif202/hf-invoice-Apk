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
import org.junit.Assert.assertFalse
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
class JobsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun job(state: JobState = JobState.SCHEDULED) = Job(
        id = "j1", title = "Move", scheduledStart = Instant.parse("2026-11-24T22:30:00Z"), scheduledEnd = Instant.parse("2026-11-25T02:30:00Z"),
        dateOnly = false, serviceDate = null, bookingStatus = BookingStatus.CONFIRMED, cancelled = false, state = state, signoff = SignoffStatus.NONE,
        reference = null, customerName = "Jane", customerPhone = null, pickup = null, destination = null, truck = null, crewSize = null,
        notes = null, assignees = emptyList(), actualStart = null, actualFinish = null, elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0
    )

    private fun snapshot(vararg jobs: Job) = JobsSnapshot(
        jobs.toList(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null
    )

    private class Fake(var snap: JobsResult<JobsSnapshot>) : JobsGateway {
        val calls = mutableListOf<String>()
        var draft: JobsResult<JSONObject> = JobsResult.Failure("none")
        override suspend fun snapshot() = snap.also { calls += "snapshot" }
        override suspend fun detail(id: String): JobsResult<Job> = JobsResult.Failure("n/a")
        override suspend fun sync(): JobsResult<String> { calls += "sync"; return JobsResult.Success("{}") }
        override suspend fun act(id: String, action: String): JobsResult<String> { calls += "act:$action"; return JobsResult.Success("{}") }
        override suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String> { calls += "correct:$reason"; return JobsResult.Success("{}") }
        override suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String> { calls += "signoff:$customerName"; return JobsResult.Success("{}") }
        override suspend fun skipSignoff(id: String, reason: String): JobsResult<String> { calls += "skip:$reason"; return JobsResult.Success("{}") }
        override suspend fun invoiceDraft(id: String) = draft
        override suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>> = JobsResult.Success(NotificationCategory.entries.associateWith { true })
        override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String> { calls += "prefs"; return JobsResult.Success("{}") }
        override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?): JobsResult<String> { calls += "saveDraft:${newCustomer?.optString("name")}"; return JobsResult.Success("{}") }
    }

    private fun vm(f: Fake) = JobsViewModel(f, onSnapshot = {})

    @Test
    fun `load shows jobs, and signed-out sessions are reported`() = runTest(dispatcher) {
        val ok = vm(Fake(JobsResult.Success(snapshot(job())))); ok.load(); advanceUntilIdle()
        assertEquals(1, ok.state.value.snapshot?.jobs?.size)
        val out = vm(Fake(JobsResult.AuthRequired)); out.load(); advanceUntilIdle()
        assertTrue(out.state.value.authRequired)
    }

    @Test
    fun `start calls the server action then refreshes, and a second tap while busy is ignored`() = runTest(dispatcher) {
        val f = Fake(JobsResult.Success(snapshot(job()))); val v = vm(f); v.load(); advanceUntilIdle(); f.calls.clear()
        v.act("j1", "start"); v.act("j1", "start"); advanceUntilIdle()
        assertEquals(listOf("act:start", "snapshot"), f.calls)
    }

    @Test
    fun `finishing opens sign-off, which is optional`() = runTest(dispatcher) {
        val f = Fake(JobsResult.Success(snapshot(job(JobState.IN_PROGRESS)))); val v = vm(f); v.load(); advanceUntilIdle()
        v.act("j1", "finish"); advanceUntilIdle()
        assertEquals("j1", v.state.value.signoffJobId)
        v.dismissSignoff(); assertNull(v.state.value.signoffJobId)
    }

    @Test
    fun `sign-off needs a name and a drawn signature before anything is sent`() = runTest(dispatcher) {
        val f = Fake(JobsResult.Success(snapshot(job(JobState.COMPLETED)))); val v = vm(f); v.load(); advanceUntilIdle(); f.calls.clear()
        v.submitSignoff("j1", "  ", "abc"); v.submitSignoff("j1", "Jane", ""); advanceUntilIdle()
        assertTrue(f.calls.isEmpty()); assertNotNull(v.state.value.message)
        v.submitSignoff("j1", "Jane Citizen", "iVBOR"); advanceUntilIdle()
        assertEquals(listOf("signoff:Jane Citizen", "snapshot"), f.calls); assertNull(v.state.value.signoffJobId)
    }

    @Test
    fun `skip sign-off keeps the reason`() = runTest(dispatcher) {
        val f = Fake(JobsResult.Success(snapshot(job(JobState.COMPLETED)))); val v = vm(f); v.load(); advanceUntilIdle(); f.calls.clear()
        v.skipSignoff("j1", "Customer Unavailable"); advanceUntilIdle()
        assertEquals("skip:Customer Unavailable", f.calls.first())
    }

    @Test
    fun `time corrections require a reason locally`() = runTest(dispatcher) {
        val f = Fake(JobsResult.Success(snapshot(job(JobState.COMPLETED)))); val v = vm(f); v.load(); advanceUntilIdle(); f.calls.clear()
        v.correct("j1", "finish", "2026-11-25T03:00:00Z", " "); advanceUntilIdle()
        assertTrue(f.calls.isEmpty())
        v.correct("j1", "finish", "2026-11-25T03:00:00Z", "Forgot to tap"); advanceUntilIdle()
        assertEquals("correct:Forgot to tap", f.calls.first())
    }

    @Test
    fun `invoice draft is reviewed first and only saved as a draft on confirmation`() = runTest(dispatcher) {
        val f = Fake(JobsResult.Success(snapshot(job(JobState.COMPLETED))))
        f.draft = JobsResult.Success(JSONObject("""{"status":"draft","warnings":["Check rate"],"workingMinutes":270,"newCustomer":{"name":"Jane"},"input":{"days":[]}}"""))
        val v = vm(f); v.load(); advanceUntilIdle(); f.calls.clear()
        v.requestInvoiceDraft("j1"); advanceUntilIdle()
        assertEquals(listOf("Check rate"), v.state.value.draft?.warnings); assertFalse(f.calls.any { it.startsWith("saveDraft") })
        v.confirmInvoiceDraft(); advanceUntilIdle()
        assertEquals("saveDraft:Jane", f.calls.first { it.startsWith("saveDraft") }); assertNull(v.state.value.draft)
    }
}
