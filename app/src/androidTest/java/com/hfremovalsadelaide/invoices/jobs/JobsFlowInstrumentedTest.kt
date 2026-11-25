package com.hfremovalsadelaide.invoices.jobs

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs fixture job flows and the notification check on the isolated QA app in an emulator. */
@RunWith(AndroidJUnit4::class)
class JobsFlowInstrumentedTest : JobsFlowBase() {
    @Test
    fun reminderNotificationHidesPrivateDetailsAndDeepLinksToTheJob() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue("notifications are enabled for the app", manager.areNotificationsEnabled())
        val job = FakeJobsGateway.fixture().copy(title = "Synthetic booking for 1 Test Street · 0400 000 000")
        val key = "e2e-test|fixture-1|upcoming_1h"
        JobNotifier.show(context, key, NotificationCategory.UPCOMING_1H, job)
        try {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var posted = manager.activeNotifications.firstOrNull { it.id == key.hashCode() }
            while (posted == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
                posted = manager.activeNotifications.firstOrNull { it.id == key.hashCode() }
            }
            assertNotNull("the reminder was posted", posted)
            val shown = checkNotNull(posted).notification
            assertNotNull(shown.publicVersion)
            assertEquals("Job reminder", shown.publicVersion.extras.getCharSequence("android.title")?.toString())
            assertEquals("Job reminder", shown.extras.getCharSequence("android.title")?.toString())
            val text = shown.extras.getCharSequence("android.text").toString()
            assertFalse(text.contains("0400"))
            assertFalse(text.contains("Test Street"))
            assertNotNull(shown.contentIntent)
            assertEquals("fixture-1", JobNotifier.openIntent(context, "fixture-1").getStringExtra(JobNotifier.EXTRA_JOB_ID))
        } finally {
            manager.cancel(key.hashCode())
        }
    }
}
