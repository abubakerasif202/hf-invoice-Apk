package com.hfremovalsadelaide.invoices.jobs

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the shared job-flow tests on the phone, plus checks that need a real notification manager. */
@RunWith(AndroidJUnit4::class)
class JobsFlowInstrumentedTest : JobsFlowBase() {
    @Test
    fun reminderNotificationHidesPrivateDetailsAndDeepLinksToTheJob() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue("notifications are enabled for the app", manager.areNotificationsEnabled())
        val job = FakeJobsGateway.fixture()
        val key = "e2e-test|fixture-1|upcoming_1h"
        JobNotifier.show(context, key, NotificationCategory.UPCOMING_1H, job)
        try {
            val n = manager.activeNotifications.firstOrNull { it.id == key.hashCode() }
            assertNotNull("the reminder was posted", n)
            val shown = n!!.notification
            assertNotNull(shown.publicVersion)
            assertEquals("Job reminder", shown.publicVersion.extras.getCharSequence("android.title")?.toString())
            val text = shown.extras.getCharSequence("android.text").toString()
            assertFalse(text.contains("0400")); assertFalse(text.contains("Test Street"))
            assertNotNull(shown.contentIntent)
            assertEquals("fixture-1", JobNotifier.openIntent(context, "fixture-1").getStringExtra(JobNotifier.EXTRA_JOB_ID))
        } finally {
            manager.cancel(key.hashCode())
        }
    }
}
