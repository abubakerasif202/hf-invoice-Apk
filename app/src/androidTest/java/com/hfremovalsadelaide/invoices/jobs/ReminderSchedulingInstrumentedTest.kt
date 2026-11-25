package com.hfremovalsadelaide.invoices.jobs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/** Reminder scheduling with the real WorkManager: unique, de-duplicated, cancelled with the booking, respects toggles. */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulingInstrumentedTest {

    private lateinit var context: Context
    private val now = Instant.now()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        JobsBackground.saveEnabled(context, NotificationCategory.entries.associateWith { true })
        context.getSharedPreferences("hf_jobs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun job(cancelled: Boolean = false, start: Instant = now.plus(Duration.ofDays(3))) =
        FakeJobsGateway.fixture().copy(scheduledStart = start, scheduledEnd = start.plus(Duration.ofHours(4)), cancelled = cancelled)

    private fun snapshot(vararg jobs: Job) = JobsSnapshot(
        jobs.toList(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyList(), true, null, null
    )

    private fun states(key: String) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(key).get().map { it.state }

    @Test
    fun syncsAndRestartsNeverDuplicateReminders() {
        val j = job()
        val keys = ReminderPlanner.plan(listOf(j), NotificationCategory.entries.associateWith { true }, now).map { it.key }
        assertEquals("24h, 1h, 15 min and start", 4, keys.size)
        JobsBackground.onSnapshot(context, snapshot(j), now)
        keys.forEach { assertEquals(listOf(WorkInfo.State.ENQUEUED), states(it)) }
        // Same data again (a re-sync, an app restart or a reboot): still exactly one pending reminder per key.
        JobsBackground.onSnapshot(context, snapshot(j), now.plusSeconds(60))
        keys.forEach { assertEquals("no duplicate for $it", 1, WorkManager.getInstance(context).getWorkInfosForUniqueWork(it).get().size) }
    }

    @Test
    fun cancellingTheBookingCancelsItsPendingReminders() {
        val j = job()
        val keys = ReminderPlanner.plan(listOf(j), NotificationCategory.entries.associateWith { true }, now).map { it.key }
        JobsBackground.onSnapshot(context, snapshot(j), now)
        JobsBackground.onSnapshot(context, snapshot(j.copy(cancelled = true)), now.plusSeconds(60))
        keys.forEach { assertTrue("$it must be cancelled", states(it).all { s -> s == WorkInfo.State.CANCELLED }) }
    }

    @Test
    fun reschedulingMovesTheRemindersAndDisabledCategoriesAreNotScheduled() {
        val j = job()
        val oldKeys = ReminderPlanner.plan(listOf(j), NotificationCategory.entries.associateWith { true }, now).map { it.key }
        JobsBackground.onSnapshot(context, snapshot(j), now)
        JobsBackground.saveEnabled(context, NotificationCategory.entries.associateWith { it != NotificationCategory.UPCOMING_1H })
        val moved = job(start = now.plus(Duration.ofDays(5)))
        JobsBackground.onSnapshot(context, snapshot(moved), now.plusSeconds(60))
        oldKeys.forEach { assertTrue("old reminder $it cancelled", states(it).all { s -> s == WorkInfo.State.CANCELLED }) }
        val newPlan = ReminderPlanner.plan(listOf(moved), NotificationCategory.entries.associateWith { it != NotificationCategory.UPCOMING_1H }, now)
        assertEquals("1 hour reminder is switched off", 3, newPlan.size)
        newPlan.forEach { assertEquals(listOf(WorkInfo.State.ENQUEUED), states(it.key)) }
    }
}
