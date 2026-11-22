package com.hfremovalsadelaide.invoices.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.pdf.DocumentDownloader
import com.hfremovalsadelaide.invoices.pdf.DownloadOutcome
import com.hfremovalsadelaide.invoices.pdf.DownloadProgress
import com.hfremovalsadelaide.invoices.pdf.DownloadedDocument
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkspaceViewModelDownloadTest {

    private val pdfUrl = "https://hf-invoice-generator.vercel.app/api/pdf/123e4567-e89b-12d3-a456-426614174000"

    private class FakeDownloader : DocumentDownloader {
        var calls = 0
        val gate = CompletableDeferred<DownloadOutcome>()
        var progressToReport: List<DownloadProgress> = emptyList()
        var progressSeen = CompletableDeferred<Unit>()

        override suspend fun download(
            url: String,
            expectedMimeType: String,
            contentDispositionHint: String?,
            openPrintImmediately: Boolean,
            onProgress: (DownloadProgress) -> Unit
        ): DownloadOutcome {
            calls++
            progressToReport.forEach(onProgress)
            progressSeen.complete(Unit)
            return gate.await()
        }

        override fun listCachedDocuments(): List<DownloadedDocument> = emptyList()
    }

    private fun document(print: Boolean = false) = DownloadOutcome.Success(
        DownloadedDocument(
            fileName = "HF-Invoice-1.pdf",
            mimeType = "application/pdf",
            localFile = File("HF-Invoice-1.pdf"),
            sizeBytes = 10,
            savedToPublicDownloads = false
        ),
        openPrintImmediately = print
    )

    @Before
    fun setMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(fake: FakeDownloader): Pair<WorkspaceViewModel, MutableList<WorkspaceEvent>> {
        val vm = WorkspaceViewModel(ApplicationProvider.getApplicationContext<Application>(), fake)
        val events = mutableListOf<WorkspaceEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events.add(it) } }
        return vm to events
    }

    @Test
    fun expiredSessionShowsSessionExpiredEventNotAGenericError() = runTest {
        val fake = FakeDownloader().apply { gate.complete(DownloadOutcome.AuthRequired) }
        val (vm, events) = viewModel(fake)

        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()

        assertTrue("events=$events", events.contains(WorkspaceEvent.SessionExpired))
        assertTrue(
            "Must not also show a generic failure",
            events.none { it is WorkspaceEvent.ShowMessage && it.messageResId != R.string.download_failed_auth }
        )
        assertFalse(vm.uiState.value.isDownloading)
        assertNull(vm.uiState.value.downloadProgress)
    }

    @Test
    fun failureOutcomeShowsItsSpecificMessage() = runTest {
        val fake = FakeDownloader().apply {
            gate.complete(DownloadOutcome.Failure(R.string.download_failed_not_found))
        }
        val (vm, events) = viewModel(fake)

        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()

        assertTrue(events.contains(WorkspaceEvent.ShowMessage(R.string.download_failed_not_found)))
        assertFalse(vm.uiState.value.isDownloading)
    }

    @Test
    fun progressIsExposedWhileDownloadingAndClearedAfterwards() = runTest {
        val fake = FakeDownloader().apply { progressToReport = listOf(DownloadProgress(2_500, 10_000)) }
        val (vm, _) = viewModel(fake)

        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()

        val during = vm.uiState.value
        assertTrue(during.isDownloading)
        assertEquals(0.25f, during.downloadProgress!!.fraction!!, 0.0001f)

        fake.gate.complete(document())
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isDownloading)
        assertNull(vm.uiState.value.downloadProgress)
        assertNotNull(vm.uiState.value.activeDocument)
    }

    @Test
    fun cancellingStopsTheDownloadAndReportsCancelled() = runTest {
        val fake = FakeDownloader()
        val (vm, events) = viewModel(fake)

        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDownloading)

        vm.cancelDownload()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isDownloading)
        assertNull(vm.uiState.value.downloadProgress)
        assertNull("A cancelled download must not produce a document", vm.uiState.value.activeDocument)
        assertTrue(events.contains(WorkspaceEvent.ShowMessage(R.string.download_cancelled)))
        assertTrue("Cancelling is not a failure", events.none {
            it is WorkspaceEvent.ShowMessage && it.messageResId == R.string.download_failed_generic
        })
    }

    @Test
    fun aSecondTapWhileBusyIsExplainedNotSilentlyDropped() = runTest {
        val fake = FakeDownloader()
        val (vm, events) = viewModel(fake)

        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()
        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()

        assertEquals("Only one download may run at a time", 1, fake.calls)
        assertTrue(events.contains(WorkspaceEvent.ShowMessage(R.string.download_already_running)))
    }

    @Test
    fun anotherDownloadCanStartAfterACancel() = runTest {
        val fake = FakeDownloader()
        val (vm, _) = viewModel(fake)

        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()
        vm.cancelDownload()
        advanceUntilIdle()
        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf")
        advanceUntilIdle()

        assertEquals(2, fake.calls)
        assertTrue(vm.uiState.value.isDownloading)
    }

    @Test
    fun printRequestEmitsPrintEventInsteadOfShowingTheSheet() = runTest {
        val fake = FakeDownloader().apply { gate.complete(document(print = true)) }
        val (vm, events) = viewModel(fake)

        vm.downloadAuthenticatedDocument(pdfUrl, "application/pdf", openPrintImmediately = true)
        advanceUntilIdle()

        assertTrue(events.any { it is WorkspaceEvent.PrintDocument })
        assertNull(vm.uiState.value.activeDocument)
    }
}
