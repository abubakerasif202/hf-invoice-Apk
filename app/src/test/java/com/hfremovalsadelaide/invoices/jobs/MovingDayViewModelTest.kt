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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MovingDayViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Fake(var stopResult: JobsResult<String> = JobsResult.Success("{}")) : JobsGateway {
        val calls = mutableListOf<String>()
        var savedStops: List<StopDraft>? = null
        private val snap = JobsSnapshot(emptyList(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null)
        override suspend fun snapshot(): JobsResult<JobsSnapshot> = JobsResult.Success(snap)
        override suspend fun detail(id: String): JobsResult<Job> = JobsResult.Failure("n/a")
        override suspend fun sync(): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun act(id: String, action: String): JobsResult<String> { calls += "act:$action"; return JobsResult.Success("{}") }
        override suspend fun correct(id: String, field: String, atIso: String, reason: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun signoff(id: String, customerName: String, pngBase64: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun skipSignoff(id: String, reason: String): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun invoiceDraft(id: String): JobsResult<JSONObject> = JobsResult.Failure("none")
        override suspend fun notificationPreferences(): JobsResult<Map<NotificationCategory, Boolean>> = JobsResult.Success(emptyMap())
        override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?): JobsResult<String> = JobsResult.Success("{}")
        override suspend fun stopEvent(id: String, stopId: String, event: String): JobsResult<String> {
            calls += "stop:$stopId:$event"
            return stopResult
        }
        override suspend fun saveStops(id: String, stops: List<StopDraft>): JobsResult<String> {
            savedStops = stops
            calls += "saveStops:${stops.size}"
            return JobsResult.Success("{}")
        }
    }

    @Test
    fun `an online stop event goes to the server once and a second tap while busy is ignored`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.recordStopEvent("j1", "s1", "arrived"); v.recordStopEvent("j1", "s1", "arrived"); advanceUntilIdle()
        assertEquals(1, f.calls.count { it.startsWith("stop:") })
        assertEquals("stop:s1:arrived", f.calls.first { it.startsWith("stop:") })
    }

    @Test
    fun `a refused stop event shows the server's reason`() = runTest(dispatcher) {
        val f = Fake(JobsResult.Failure("Record arrival at this stop before completing it.")); val v = JobsViewModel(f)
        v.recordStopEvent("j1", "s1", "completed"); advanceUntilIdle()
        assertEquals("Record arrival at this stop before completing it.", v.state.value.message)
    }

    @Test
    fun `travelling and arrived use the ordinary action path`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.act("j1", "travelling"); advanceUntilIdle()
        assertTrue(f.calls.contains("act:travelling"))
    }

    @Test
    fun `saving stops sends them in order, and a blank address never leaves the device`() = runTest(dispatcher) {
        val f = Fake(); val v = JobsViewModel(f)
        v.saveStops("j1", listOf(StopDraft(null, StopKind.PICKUP, "1 A St"), StopDraft(null, StopKind.DELIVERY, "9 Z Rd"))); advanceUntilIdle()
        assertEquals(listOf("1 A St", "9 Z Rd"), f.savedStops?.map { it.address })

        val g = Fake(); val w = JobsViewModel(g)
        w.saveStops("j1", listOf(StopDraft(null, StopKind.PICKUP, "  "))); advanceUntilIdle()
        assertTrue(g.calls.isEmpty())
        assertNotNull(w.state.value.message)
    }
}
