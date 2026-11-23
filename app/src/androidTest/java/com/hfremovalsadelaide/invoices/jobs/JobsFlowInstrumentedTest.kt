package com.hfremovalsadelaide.invoices.jobs

import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hfremovalsadelaide.invoices.ui.jobs.JobsTab
import com.hfremovalsadelaide.invoices.ui.theme.HFRemovalsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device end-to-end tests of the job workflow. They run against an in-memory fixture (FakeJobsGateway),
 * so no real booking, signature, invoice or network call is involved.
 */
@RunWith(AndroidJUnit4::class)
class JobsFlowInstrumentedTest {

    @get:Rule
    val rule = createComposeRule()

    private val pdfRequests = mutableListOf<String>()

    private fun launch(fake: FakeJobsGateway): JobsViewModel {
        val vm = JobsViewModel(fake)
        rule.setContent {
            HFRemovalsTheme {
                val state by vm.state.collectAsState()
                JobsTab(state = state, viewModel = vm, onGoToSignIn = {}, onDownloadCompletionPdf = { pdfRequests += it })
            }
        }
        vm.load()
        return vm
    }

    private fun waitForTag(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithTagCount(tag) > 0
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun openFixture(fake: FakeJobsGateway) {
        launch(fake)
        waitForTag("job_card_fixture-1")
        rule.onNodeWithTag("job_card_fixture-1").performClick()
    }

    @Test
    fun calendarShowsBookingAndDetailsSeparateScheduledFromActual() {
        openFixture(FakeJobsGateway(listOf(FakeJobsGateway.fixture())))
        rule.onNodeWithText("Scheduled (from calendar)").assertExists()
        rule.onNodeWithText("Actual (recorded)").assertExists()
        rule.onNodeWithText("Not provided", substring = true).assertExists() // nothing recorded yet, never invented
        rule.onNodeWithTag("job_call").assertExists()
        rule.onNodeWithTag("job_start").assertIsEnabled()
    }

    @Test
    fun startPauseResumeFinishRecordsActualTimesAndOpensOptionalSignoff() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture()))
        openFixture(fake)
        rule.onNodeWithTag("job_start").performClick()
        waitForTag("job_pause_resume")
        rule.onNodeWithTag("job_pause_resume").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Resume").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("job_pause_resume").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Pause (break)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("job_finish").performClick()
        rule.onNodeWithText("Finish this job?").assertExists()
        rule.onAllNodesWithText("Finish Job").onLast().performClick()
        waitForTag("signoff_logo") // the HF logo is shown on the completion & sign-off screen
        assertEquals(listOf("act:start", "act:pause", "act:resume", "act:finish"), fake.calls.filter { it.startsWith("act:") })
    }

    @Test
    fun signatureCanBeDrawnClearedAndSavedAndNeedsAcknowledgement() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture(JobState.IN_PROGRESS)))
        openFixture(fake)
        rule.onNodeWithTag("job_finish").performClick()
        rule.onAllNodesWithText("Finish Job").onLast().performClick()
        waitForTag("signature_canvas")
        rule.onNodeWithTag("signoff_confirm").assertIsNotEnabled() // nothing drawn or acknowledged yet
        rule.onNodeWithTag("signature_canvas").performTouchInput { swipe(Offset(30f, 40f), Offset(300f, 120f), 300) }
        rule.onNodeWithTag("signature_clear").performClick()
        rule.onNodeWithTag("signoff_ack").performClick()
        rule.onNodeWithTag("signoff_confirm").assertIsNotEnabled() // cleared, so still no signature
        rule.onNodeWithTag("signature_canvas").performTouchInput { swipe(Offset(30f, 60f), Offset(320f, 100f), 300) }
        rule.onNodeWithTag("signoff_confirm").assertIsEnabled()
        rule.onNodeWithTag("signoff_confirm").performClick()
        rule.waitUntil(5_000) { fake.calls.contains("signoff") }
        assertEquals("Test Customer", fake.lastSignoffName)
        assertTrue("signature is a PNG", fake.lastSignature.orEmpty().startsWith("iVBOR"))
    }

    @Test
    fun skippingSignoffRecordsTheReasonAndKeepsTheJobCompleted() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture(JobState.IN_PROGRESS)))
        openFixture(fake)
        rule.onNodeWithTag("job_finish").performClick()
        rule.onAllNodesWithText("Finish Job").onLast().performClick()
        waitForTag("signoff_skip")
        rule.onNodeWithTag("signoff_skip").performClick()
        rule.onNodeWithTag("signoff_skip_confirm").performClick()
        rule.waitUntil(5_000) { fake.calls.contains("skip") }
        assertEquals("Customer Unavailable", fake.lastSkipReason)
    }

    @Test
    fun completedJobCanRequestPdfAndOnlyASavedDraftInvoice() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture(JobState.IN_PROGRESS)))
        openFixture(fake)
        rule.onNodeWithTag("job_finish").performClick()
        rule.onAllNodesWithText("Finish Job").onLast().performClick()
        waitForTag("signoff_later")
        rule.onNodeWithTag("signoff_later").performClick()
        waitForTag("job_pdf")
        rule.onNodeWithTag("job_pdf").performClick()
        assertEquals(listOf("fixture-1"), pdfRequests)
        rule.onNodeWithTag("job_create_invoice").performClick()
        rule.onNodeWithText("Review draft invoice").assertExists()
        rule.onNode(hasText("No matching truck package", substring = true)).assertExists()
        assertTrue("reviewing must not save anything", fake.savedDrafts.isEmpty())
        rule.onNodeWithTag("draft_confirm").performClick()
        rule.waitUntil(5_000) { fake.savedDrafts.size == 1 }
        assertFalse(fake.calls.any { it.contains("issue") })
    }

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
