package com.hfremovalsadelaide.invoices.ui

import com.hfremovalsadelaide.invoices.jobs.BookingStatus
import com.hfremovalsadelaide.invoices.jobs.ConditionReportItem
import com.hfremovalsadelaide.invoices.jobs.InventoryStatus
import com.hfremovalsadelaide.invoices.jobs.Job
import com.hfremovalsadelaide.invoices.jobs.JobSnapshotCodec
import com.hfremovalsadelaide.invoices.jobs.JobState
import com.hfremovalsadelaide.invoices.jobs.JobStatusFilter
import com.hfremovalsadelaide.invoices.jobs.JobsGateway
import com.hfremovalsadelaide.invoices.jobs.JobsResult
import com.hfremovalsadelaide.invoices.jobs.JobsSnapshot
import com.hfremovalsadelaide.invoices.jobs.JobsViewModel
import com.hfremovalsadelaide.invoices.jobs.NotificationCategory
import com.hfremovalsadelaide.invoices.jobs.ReminderText
import com.hfremovalsadelaide.invoices.jobs.SignoffStatus
import com.hfremovalsadelaide.invoices.offline.CipherBox
import com.hfremovalsadelaide.invoices.offline.EncryptedStore
import com.hfremovalsadelaide.invoices.offline.OfflineOpKind
import com.hfremovalsadelaide.invoices.offline.OfflineSyncQueue
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlatformViewModelsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun sampleJob(
        id: String = "job-1",
        customerName: String = "Liam O'Connor",
        truck: String = "MR 12t",
        bookingStatus: BookingStatus = BookingStatus.CONFIRMED,
        cancelled: Boolean = false,
        state: JobState = JobState.SCHEDULED
    ) = Job(
        id = id,
        title = "Glenelg Move - $customerName",
        scheduledStart = Instant.parse("2026-11-25T00:00:00Z"),
        scheduledEnd = Instant.parse("2026-11-25T04:00:00Z"),
        dateOnly = false,
        serviceDate = "2026-11-25",
        bookingStatus = bookingStatus,
        cancelled = cancelled,
        state = state,
        signoff = SignoffStatus.NONE,
        reference = "HF-901",
        customerName = customerName,
        customerPhone = "0411 222 333",
        pickup = "12 Jetty Rd, Glenelg SA",
        destination = "45 King William St, Adelaide SA",
        truck = truck,
        crewSize = 2,
        notes = "Stairs at pickup",
        assignees = listOf("Sam"),
        actualStart = null,
        actualFinish = null,
        elapsedMinutes = 0,
        breakMinutes = 0,
        workingMinutes = 0,
        travelNotes = "Allow 20 min city traffic",
        dispatchNotes = "Bring piano trolley",
        missingInfo = emptyList(),
        waitingMinutes = 15,
        overtimeMinutes = 0,
        waitActive = false,
        updatedAt = "2026-11-24T10:00:00.000Z"
    )

    @Test
    fun `job snapshot codec round-trips new dispatch and waiting fields`() {
        val snap = JobsSnapshot(
            jobs = listOf(sampleJob()),
            todayConfirmed = setOf("job-1"),
            upcoming = emptySet(),
            inProgress = emptySet(),
            completed = emptySet(),
            awaitingSignoff = emptySet(),
            unconfirmed = emptySet(),
            conflicts = listOf("job-1" to "job-2"),
            calendarConnected = true,
            lastSyncedAt = "2026-11-25T00:00:00Z",
            lastError = null,
            missingInfoIds = setOf("job-2"),
            twoWaySyncEnabled = false
        )
        val encoded = JobSnapshotCodec.encode(snap)
        val decoded = JobSnapshotCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals("Allow 20 min city traffic", decoded!!.jobs.single().travelNotes)
        assertEquals(15, decoded.jobs.single().waitingMinutes)
        assertEquals(listOf("job-1" to "job-2"), decoded.conflicts)
        assertEquals(setOf("job-2"), decoded.missingInfoIds)
    }

    @Test
    fun `jobs viewmodel queues offline actions and falls back to encrypted cache`() = runTest(dispatcher) {
        val ctx = RuntimeEnvironment.getApplication()
        val store = EncryptedStore(ctx, object : CipherBox {
            override fun encrypt(plainText: String) = plainText.toByteArray()
            override fun decrypt(cipherBlob: ByteArray) = String(cipherBlob)
        }).apply { clearAll() }
        val queue = OfflineSyncQueue(store).apply { bindAccount("driver-one") }
        var online = true
        val initialSnap = JobsSnapshot(
            listOf(sampleJob()), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null
        )

        val stubGateway = object : JobsGateway {
            override suspend fun snapshot() = JobsResult.Success(initialSnap)
            override suspend fun detail(id: String) = JobsResult.Success(sampleJob())
            override suspend fun sync() = JobsResult.Success("{}")
            override suspend fun act(id: String, action: String) = JobsResult.Success("{}")
            override suspend fun correct(id: String, field: String, atIso: String, reason: String) = JobsResult.Success("{}")
            override suspend fun signoff(id: String, customerName: String, pngBase64: String) = JobsResult.Success("{}")
            override suspend fun skipSignoff(id: String, reason: String) = JobsResult.Success("{}")
            override suspend fun invoiceDraft(id: String) = JobsResult.Failure("n/a")
            override suspend fun notificationPreferences() = JobsResult.Success(emptyMap<NotificationCategory, Boolean>())
            override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>) = JobsResult.Success("{}")
            override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?) = JobsResult.Success("{}")
        }

        val vm = JobsViewModel(
            gateway = stubGateway,
            syncQueue = queue,
            cacheRead = { store.readText("jobs_snapshot_cache") },
            cacheWrite = { store.writeText("jobs_snapshot_cache", it) },
            isNetworkAvailable = { online }
        )

        vm.load()
        advanceUntilIdle()
        assertFalse(vm.state.value.isFromOfflineCache)

        online = false
        vm.act("job-1", "start")
        advanceUntilIdle()
        vm.scanBarcode("job-1", "HFR-10", InventoryStatus.LOADED)
        advanceUntilIdle()
        vm.saveConditionReport(
            "job-1",
            ConditionReportItem("", "before", "2 flights stairs", "None", "", "", mapOf("Access" to true), false, "", "")
        )
        advanceUntilIdle()

        assertEquals(3, queue.pendingCount)
        assertEquals(
            listOf(OfflineOpKind.JOB_TIME_ACTION, OfflineOpKind.INVENTORY_SCAN, OfflineOpKind.CONDITION_REPORT),
            queue.operations.value.map { it.kind }
        )
        assertEquals(JobState.IN_PROGRESS, vm.state.value.snapshot?.jobs?.single()?.state)

        // New VM while offline loads encrypted cached snapshot immediately
        val offlineVm = JobsViewModel(
            gateway = stubGateway,
            syncQueue = queue,
            cacheRead = { store.readText("jobs_snapshot_cache") },
            cacheWrite = { store.writeText("jobs_snapshot_cache", it) },
            isNetworkAvailable = { false }
        )
        offlineVm.load()
        advanceUntilIdle()
        assertTrue(offlineVm.state.value.isFromOfflineCache)
        assertEquals(1, offlineVm.state.value.snapshot?.jobs?.size)
    }

    @Test
    fun `jobs ui state search and status filters operate cleanly`() = runTest(dispatcher) {
        val snap = JobsSnapshot(
            jobs = listOf(
                sampleJob("1", "Alice", "Small 8t", BookingStatus.CONFIRMED, false, JobState.SCHEDULED),
                sampleJob("2", "Bob", "HR 16t", BookingStatus.UNCONFIRMED, false, JobState.COMPLETED),
                sampleJob("3", "Cancelled Person", "MR 12t", BookingStatus.CONFIRMED, true, JobState.SCHEDULED)
            ),
            todayConfirmed = emptySet(), upcoming = emptySet(), inProgress = emptySet(),
            completed = emptySet(), awaitingSignoff = emptySet(), unconfirmed = emptySet(),
            conflicts = emptyList(), calendarConnected = true, lastSyncedAt = null, lastError = null
        )
        val vm = JobsViewModel(object : JobsGateway {
            override suspend fun snapshot() = JobsResult.Success(snap)
            override suspend fun detail(id: String) = JobsResult.Failure("n/a")
            override suspend fun sync() = JobsResult.Success("{}")
            override suspend fun act(id: String, action: String) = JobsResult.Success("{}")
            override suspend fun correct(id: String, field: String, atIso: String, reason: String) = JobsResult.Success("{}")
            override suspend fun signoff(id: String, customerName: String, pngBase64: String) = JobsResult.Success("{}")
            override suspend fun skipSignoff(id: String, reason: String) = JobsResult.Success("{}")
            override suspend fun invoiceDraft(id: String) = JobsResult.Failure("n/a")
            override suspend fun notificationPreferences() = JobsResult.Success(emptyMap<NotificationCategory, Boolean>())
            override suspend fun savePreferences(prefs: Map<NotificationCategory, Boolean>) = JobsResult.Success("{}")
            override suspend fun saveInvoiceDraft(input: JSONObject, newCustomer: JSONObject?) = JobsResult.Success("{}")
        })
        vm.load()
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), vm.state.value.filteredJobs().map { it.id })
        vm.onStatusFilterSelected(JobStatusFilter.CANCELLED)
        assertEquals(listOf("3"), vm.state.value.filteredJobs().map { it.id })
        vm.onStatusFilterSelected(JobStatusFilter.ALL)
        vm.onSearchQueryChanged("bob")
        assertEquals(listOf("2"), vm.state.value.filteredJobs().map { it.id })
    }

    @Test
    fun `lockscreen notifications never expose private details across all categories`() {
        val job = sampleJob()
        NotificationCategory.entries.forEach { cat ->
            val pub = ReminderText.publicVersion(cat)
            val body = ReminderText.body(cat, job)
            listOf(pub, body).forEach { txt ->
                assertFalse(txt.contains("0411"))
                assertFalse(txt.contains("Jetty Rd"))
                assertFalse(txt.contains("Liam"))
            }
        }
    }
}
