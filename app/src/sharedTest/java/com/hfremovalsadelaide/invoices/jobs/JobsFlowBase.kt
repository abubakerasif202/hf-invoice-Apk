package com.hfremovalsadelaide.invoices.jobs

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hfremovalsadelaide.invoices.ui.jobs.JobsTab
import com.hfremovalsadelaide.invoices.ui.theme.HFRemovalsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * On-device end-to-end tests of the job workflow. They run against an in-memory fixture (FakeJobsGateway),
 * so no real booking, signature, invoice or network call is involved.
 */
@RunWith(AndroidJUnit4::class)
abstract class JobsFlowBase {

    @get:Rule
    val rule = createComposeRule()

    private val pdfRequests = mutableListOf<Pair<String, Boolean>>()

    private fun launch(fake: FakeJobsGateway): JobsViewModel {
        val vm = JobsViewModel(fake)
        rule.setContent {
            HFRemovalsTheme {
                val state by vm.state.collectAsState()
                JobsTab(
                    state = state,
                    viewModel = vm,
                    onGoToSignIn = {},
                    onDownloadCompletionPdf = { jobId, includePhotos -> pdfRequests += jobId to includePhotos },
                    permissions = setOf("jobs.read", "jobs.act", "jobs.crew", "jobs.signoff", "invoice.draft", "invoices.manage")
                )
            }
        }
        vm.load()
        return vm
    }

    private fun waitForTag(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun finishJob() {
        rule.onNodeWithTag("job_finish").performScrollTo().performClick()
        waitForTag("job_finish_confirm")
        rule.onNodeWithTag("job_finish_confirm").performClick()
    }

    private fun waitForBreakAction(label: String) {
        val action = hasTestTag("job_pause_resume") and hasText(label) and isEnabled()
        rule.waitUntil(5_000) { rule.onAllNodes(action).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun toggleBreak(label: String) {
        waitForBreakAction(label)
        rule.onNodeWithTag("job_pause_resume").performScrollTo().performClick()
    }

    private fun openFixture(fake: FakeJobsGateway) {
        launch(fake)
        waitForTag("jobs_list")
        // The list is lazy: scroll to the booking the way a user would before tapping it.
        rule.onNodeWithTag("jobs_list").performScrollToNode(hasTestTag("job_card_fixture-1"))
        rule.onNodeWithTag("job_card_fixture-1").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Scheduled (from calendar)").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun calendarShowsBookingAndDetailsSeparateScheduledFromActual() {
        openFixture(FakeJobsGateway(listOf(FakeJobsGateway.fixture())))
        rule.onNodeWithText("Scheduled (from calendar)").assertExists()
        rule.onNodeWithText("Actual (recorded)").assertExists()
        rule.onAllNodesWithText("Not provided", substring = true).onFirst().assertExists() // nothing recorded yet, never invented
        rule.onNodeWithTag("job_call").assertExists()
        rule.onNodeWithTag("job_start").assertIsEnabled()
    }

    @Test
    fun crewDepartureUsesTheActualLocalTimeEntered() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture(JobState.IN_PROGRESS)))
        openFixture(fake)
        rule.onNodeWithTag("job_crew_event_btn").performScrollTo().performClick()
        rule.onNodeWithTag("crew_event_name").performTextInput("Test Mover")
        rule.onNodeWithText("Left").performClick()
        rule.onNodeWithTag("crew_event_time").performScrollTo().performTextInput("2026-11-24 10:30")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5_000) { fake.lastCrewEvent != null }
        assertEquals("Test Mover", fake.lastCrewEvent?.memberName)
        assertEquals("left", fake.lastCrewEvent?.eventType)
        assertEquals(Instant.parse("2026-11-24T00:00:00Z"), fake.lastCrewEvent?.at)
    }

    @Test
    fun movingDayRecordsJourneyAndCompletesStopsInDispatchOrder() {
        val pickup = JobStop("pickup", 1, StopKind.PICKUP, "1 Test Street", "", "", "", null, null)
        val delivery = JobStop("delivery", 2, StopKind.DELIVERY, "2 Test Street", "", "", "", null, null)
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture().copy(stops = listOf(delivery, pickup))))
        openFixture(fake)

        rule.onNodeWithText("Journey").assertExists()
        rule.onNodeWithTag("job_travelling_btn").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Travelling since", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("job_arrived_btn").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("travel 30 min", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        val firstStop = rule.onNodeWithTag("stop_card_1").fetchSemanticsNode().positionInRoot.y
        val secondStop = rule.onNodeWithTag("stop_card_2").fetchSemanticsNode().positionInRoot.y
        assertTrue(firstStop < secondStop)
        rule.onNodeWithTag("job_start").performScrollTo().performClick()
        for (seq in 1..2) {
            waitForTag("stop_arrived_$seq")
            rule.onNodeWithTag("stop_arrived_$seq").performScrollTo().performClick()
            waitForTag("stop_done_$seq")
            rule.onNodeWithTag("stop_done_$seq").performScrollTo().performClick()
        }
        finishJob()
        waitForTag("signoff_logo")
        assertEquals(
            listOf("act:travelling", "act:arrived", "act:start", "stop:pickup:arrived",
                "stop:pickup:completed", "stop:delivery:arrived", "stop:delivery:completed", "act:finish"),
            fake.calls.filter { it.startsWith("act:") || it.startsWith("stop:") }
        )
    }

    @Test
    fun startPauseResumeFinishRecordsActualTimesAndOpensOptionalSignoff() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture()))
        openFixture(fake)
        rule.onNodeWithTag("job_start").performClick()
        toggleBreak("Pause (break)")
        toggleBreak("Resume")
        waitForBreakAction("Pause (break)")
        finishJob()
        waitForTag("signoff_logo") // the HF logo is shown on the completion & sign-off screen
        assertEquals(listOf("act:start", "act:pause", "act:resume", "act:finish"), fake.calls.filter { it.startsWith("act:") })
    }

    @Test
    fun signatureCanBeDrawnClearedAndSavedAndNeedsAcknowledgement() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture(JobState.IN_PROGRESS)))
        openFixture(fake)
        finishJob()
        waitForTag("signature_canvas")
        rule.onNodeWithTag("signoff_confirm").assertIsNotEnabled() // nothing drawn or acknowledged yet
        rule.onNodeWithTag("signature_canvas").performTouchInput { swipe(Offset(width * 0.15f, height * 0.35f), Offset(width * 0.85f, height * 0.65f), 300) }
        rule.onNodeWithTag("signature_clear").performClick()
        rule.onNodeWithTag("signoff_ack").performScrollTo().performClick()
        rule.onNodeWithTag("signoff_confirm").performScrollTo().assertIsNotEnabled() // cleared, so still no signature
        rule.onNodeWithTag("signature_canvas").performScrollTo().performTouchInput { swipe(Offset(width * 0.15f, height * 0.35f), Offset(width * 0.85f, height * 0.65f), 300) }
        rule.onNodeWithTag("signoff_confirm").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("signoff_confirm").performScrollTo().performClick()
        rule.waitUntil(5_000) { fake.calls.contains("signoff") }
        assertEquals("Test Customer", fake.lastSignoffName)
        assertTrue("signature is a PNG", fake.lastSignature.orEmpty().startsWith("iVBOR"))
    }

    @Test
    fun skippingSignoffRecordsTheReasonAndKeepsTheJobCompleted() {
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture(JobState.IN_PROGRESS)))
        openFixture(fake)
        finishJob()
        waitForTag("signoff_skip")
        rule.onNodeWithTag("signoff_skip").performScrollTo().performClick()
        rule.onNodeWithTag("signoff_skip_confirm").performScrollTo().performClick()
        rule.waitUntil(5_000) { fake.calls.contains("skip") }
        assertEquals("Customer Unavailable", fake.lastSkipReason)
    }

    @Test
    fun completedJobCanRequestPdfAndOnlyASavedDraftInvoice() {
        val photo = JobPhotoMeta("synthetic", "after", null, "Fixture photo", "image/png", "2026-11-24T01:00:00Z")
        val fake = FakeJobsGateway(listOf(FakeJobsGateway.fixture(JobState.IN_PROGRESS).copy(photos = listOf(photo))))
        openFixture(fake)
        finishJob()
        waitForTag("signoff_later")
        rule.onNodeWithTag("signoff_later").performScrollTo().performClick()
        waitForTag("job_pdf")
        rule.onNodeWithTag("job_pdf").performClick()
        assertEquals(listOf("fixture-1" to false), pdfRequests)
        rule.onNodeWithText("Completion PDF with photos").performScrollTo().performClick()
        assertEquals(listOf("fixture-1" to false, "fixture-1" to true), pdfRequests)
        rule.onNodeWithTag("job_create_invoice").performClick()
        waitForTag("draft_confirm")
        rule.onNodeWithText("Review draft invoice").assertExists()
        rule.onNode(hasText("No matching truck package", substring = true)).assertExists()
        assertTrue("reviewing must not save anything", fake.savedDrafts.isEmpty())
        rule.onNodeWithTag("draft_confirm").performClick()
        rule.waitUntil(5_000) { fake.savedDrafts.size == 1 }
        assertFalse(fake.calls.any { it.contains("issue") })
    }
}
