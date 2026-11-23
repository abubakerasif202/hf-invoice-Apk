package com.hfremovalsadelaide.invoices.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hfremovalsadelaide.invoices.R
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthenticatedDocumentDownloaderTest {

    private val pdfUrl = "https://hf-invoice-generator.vercel.app/api/pdf/123e4567-e89b-12d3-a456-426614174000"
    private val csvUrl = "https://hf-invoice-generator.vercel.app/api/export"
    private val pdfBytes = "%PDF-1.7\n".toByteArray() + ByteArray(40_000) { (it % 251).toByte() }

    private lateinit var context: Context
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir = File(context.cacheDir, "invoices").apply { deleteRecursively() }
    }

    private fun downloader(
        connection: (URL) -> FakeHttpConnection,
        cookie: (String) -> String? = { "session=abc" },
        factoryCalls: MutableList<String>? = null
    ) = AuthenticatedDocumentDownloader(
        context = context,
        connectionFactory = { url ->
            factoryCalls?.add(url.toString())
            connection(url)
        },
        cookieProvider = cookie
    )

    private fun pdfConnection(
        url: URL,
        status: Int = 200,
        body: ByteArray = pdfBytes,
        headers: Map<String, String> = mapOf(
            "Content-Type" to "application/pdf",
            "Content-Disposition" to """attachment; filename="HF-Invoice-INV-0042.pdf""""
        )
    ) = FakeHttpConnection(
        url = url,
        status = status,
        headers = headers,
        bodyStream = { ByteArrayInputStream(body) },
        announcedLength = body.size.toLong()
    )

    private fun download(d: AuthenticatedDocumentDownloader, url: String = pdfUrl, mime: String = "application/pdf") =
        runBlocking { d.download(url = url, expectedMimeType = mime) }

    // ---- Session expiry -------------------------------------------------------------------

    @Test
    fun http401IsReportedAsSessionExpired() {
        val outcome = download(downloader({ FakeHttpConnection(it, status = 401) }))
        assertEquals(DownloadOutcome.AuthRequired, outcome)
    }

    @Test
    fun redirectToLoginPageIsReportedAsSessionExpired() {
        listOf(301, 302, 303, 307, 308).forEach { code ->
            val outcome = download(
                downloader({
                    FakeHttpConnection(it, status = code, headers = mapOf("Location" to "/login?next=%2Fapi%2Fpdf%2F1"))
                })
            )
            assertEquals("HTTP $code to /login should mean session expired", DownloadOutcome.AuthRequired, outcome)
        }
    }

    @Test
    fun absoluteSameOriginRedirectIsReportedAsSessionExpired() {
        val outcome = download(
            downloader({
                FakeHttpConnection(
                    it,
                    status = 302,
                    headers = mapOf("Location" to "https://hf-invoice-generator.vercel.app/")
                )
            })
        )
        assertEquals(DownloadOutcome.AuthRequired, outcome)
    }

    @Test
    fun htmlLoginPageServedWithStatus200IsReportedAsSessionExpired() {
        val html = "<html><body>Administrator password</body></html>".toByteArray()
        val outcome = download(
            downloader({
                FakeHttpConnection(
                    it,
                    headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
                    bodyStream = { ByteArrayInputStream(html) }
                )
            })
        )
        assertEquals(DownloadOutcome.AuthRequired, outcome)
    }

    @Test
    fun redirectToUntrustedHostIsBlockedNotFollowedAndNotTreatedAsSignIn() {
        val calls = mutableListOf<String>()
        val outcome = download(
            downloader(
                { FakeHttpConnection(it, status = 302, headers = mapOf("Location" to "https://evil.example/steal")) },
                factoryCalls = calls
            )
        )
        assertEquals(DownloadOutcome.Failure(R.string.download_failed_redirect), outcome)
        assertEquals("Redirect must not be followed", listOf(pdfUrl), calls)
    }

    // ---- Error classification -------------------------------------------------------------

    @Test
    fun http404ExplainsDocumentIsMissing() {
        val outcome = download(downloader({ FakeHttpConnection(it, status = 404) }))
        assertEquals(DownloadOutcome.Failure(R.string.download_failed_not_found), outcome)
    }

    @Test
    fun http5xxExplainsServerProblem() {
        listOf(500, 502, 503).forEach { code ->
            val outcome = download(downloader({ FakeHttpConnection(it, status = code) }))
            assertEquals("HTTP $code", DownloadOutcome.Failure(R.string.download_failed_server), outcome)
        }
    }

    @Test
    fun unknownHostExplainsNoConnection() {
        val outcome = download(downloader({ FakeHttpConnection(it, failOnResponse = UnknownHostException("x")) }))
        assertEquals(DownloadOutcome.Failure(R.string.download_failed_network), outcome)
    }

    @Test
    fun socketTimeoutExplainsSlowServer() {
        val outcome = download(downloader({ FakeHttpConnection(it, failOnResponse = SocketTimeoutException("slow")) }))
        assertEquals(DownloadOutcome.Failure(R.string.download_failed_timeout), outcome)
    }

    @Test
    fun announcedOversizedDocumentIsRejectedBeforeReading() {
        var opened = false
        val outcome = download(
            downloader({
                FakeHttpConnection(
                    it,
                    headers = mapOf("Content-Type" to "application/pdf"),
                    announcedLength = 200L * 1024 * 1024,
                    bodyStream = { opened = true; ByteArrayInputStream(pdfBytes) }
                )
            })
        )
        assertEquals(DownloadOutcome.Failure(R.string.download_failed_too_large), outcome)
        assertFalse("Body must not be read when length is announced as too large", opened)
    }

    @Test
    fun nonPdfBodyWithPdfContentTypeIsRejected() {
        val outcome = download(
            downloader({
                pdfConnection(it, body = "not a pdf at all".toByteArray())
            })
        )
        assertEquals(DownloadOutcome.Failure(R.string.download_failed_invalid_pdf), outcome)
    }

    // ---- Security ---------------------------------------------------------------------------

    @Test
    fun untrustedUrlNeverOpensAConnectionOrReadsCookies() {
        val calls = mutableListOf<String>()
        var cookieReads = 0
        val outcome = download(
            downloader(
                { pdfConnection(it) },
                cookie = { cookieReads++; "session=secret" },
                factoryCalls = calls
            ),
            url = "https://hf-invoice-generator.vercel.app.evil.com/api/pdf/1"
        )
        assertEquals(DownloadOutcome.Failure(R.string.untrusted_link_blocked), outcome)
        assertTrue(calls.isEmpty())
        assertEquals(0, cookieReads)
    }

    @Test
    fun sessionCookieIsSentOnlyAsRequestHeaderToTrustedHost() {
        var captured: FakeHttpConnection? = null
        download(downloader({ pdfConnection(it).also { c -> captured = c } }))
        assertEquals("session=abc", captured!!.getRequestProperty("Cookie"))
        assertFalse(captured!!.instanceFollowRedirects)
    }

    // ---- Success & progress -----------------------------------------------------------------

    @Test
    fun successKeepsServerFilenameAndStoresPrivateCopy() {
        val outcome = download(downloader({ pdfConnection(it) }))
        outcome as DownloadOutcome.Success
        assertEquals("HF-Invoice-INV-0042.pdf", outcome.document.fileName)
        assertEquals(pdfBytes.size.toLong(), outcome.document.sizeBytes)
        assertTrue(outcome.document.localFile.exists())
        assertTrue(outcome.document.localFile.absolutePath.startsWith(context.cacheDir.absolutePath))
    }

    @Test
    fun progressIsReportedMonotonicallyAndReachesTotal() {
        val reports = mutableListOf<DownloadProgress>()
        val d = downloader({ pdfConnection(it) })
        val outcome = runBlocking {
            d.download(pdfUrl, "application/pdf", onProgress = { reports.add(it) })
        }
        assertTrue(outcome is DownloadOutcome.Success)
        assertTrue("Expected several progress callbacks, got ${reports.size}", reports.size >= 3)
        assertEquals(reports.sortedBy { it.bytesRead }, reports)
        assertEquals(pdfBytes.size.toLong(), reports.last().bytesRead)
        assertEquals(pdfBytes.size.toLong(), reports.last().totalBytes)
        assertEquals(1f, reports.last().fraction!!, 0.0001f)
    }

    @Test
    fun progressIsIndeterminateWhenServerDoesNotAnnounceLength() {
        val reports = mutableListOf<DownloadProgress>()
        val d = downloader({
            FakeHttpConnection(
                it,
                headers = mapOf("Content-Type" to "application/pdf"),
                bodyStream = { ByteArrayInputStream(pdfBytes) }
            )
        })
        runBlocking { d.download(pdfUrl, "application/pdf", onProgress = { reports.add(it) }) }
        assertTrue(reports.isNotEmpty())
        assertTrue(reports.all { it.totalBytes == null && it.fraction == null })
    }

    @Test
    fun csvExportIsStoredWithCsvExtension() {
        val csv = "id,total\n1,100.00\n".toByteArray()
        val outcome = download(
            downloader({
                FakeHttpConnection(
                    it,
                    headers = mapOf("Content-Type" to "text/csv; charset=utf-8"),
                    bodyStream = { ByteArrayInputStream(csv) },
                    announcedLength = csv.size.toLong()
                )
            }),
            url = csvUrl,
            mime = "text/csv"
        ) as DownloadOutcome.Success
        assertTrue(outcome.document.fileName.endsWith(".csv"))
        assertEquals("text/csv", outcome.document.mimeType)
    }

    // ---- Cancellation -----------------------------------------------------------------------

    @Test
    fun cancellingMidDownloadStopsReadingDisconnectsAndLeavesNoFile() {
        val started = CompletableDeferred<Unit>()
        var connection: FakeHttpConnection? = null
        val blockingStream = object : InputStream() {
            private var served = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served == 0) {
                    served = 4096
                    System.arraycopy(pdfBytes, 0, b, off, 4096)
                    started.complete(Unit)
                    return 4096
                }
                // Simulate a stalled server: block until the connection is torn down.
                val deadline = System.currentTimeMillis() + 5_000
                while (connection?.disconnected != true && System.currentTimeMillis() < deadline) Thread.sleep(10)
                throw IOException("socket closed")
            }
        }
        val d = downloader({
            FakeHttpConnection(
                it,
                headers = mapOf("Content-Type" to "application/pdf"),
                bodyStream = { blockingStream },
                announcedLength = pdfBytes.size.toLong()
            ).also { c -> connection = c }
        })

        runBlocking {
            val job = async(Dispatchers.Default, start = CoroutineStart.DEFAULT) { d.download(pdfUrl, "application/pdf") }
            withTimeout(5_000) { started.await() }
            // The stalled read only ends when the connection is torn down, so a prompt join
            // proves cancellation disconnects instead of waiting for the read to time out.
            withTimeout(2_000) { job.cancelAndJoin() }
            assertTrue("Job must end cancelled, not completed with a Failure", job.isCancelled)
        }
        assertTrue("Connection must be disconnected on cancel", connection!!.disconnected)
        assertTrue("No partial file may remain", cacheDir.listFiles().orEmpty().isEmpty())
    }

    // ---- Privacy of signed completion documents --------------------------------------------

    private val completionUrl = "https://hf-invoice-generator.vercel.app/api/jobs/123e4567-e89b-42d3-a456-426614174000/completion-pdf"

    @Test
    fun completionPdfStaysPrivateAndIsNeverSavedToPublicDownloads() {
        val outcome = download(downloader({ pdfConnection(it) }), url = completionUrl)
        outcome as DownloadOutcome.Success
        assertFalse("signed documents must not be copied into shared storage automatically", outcome.document.savedToPublicDownloads)
        assertTrue(outcome.document.isPrivate)
        assertTrue(outcome.document.localFile.exists())
        assertTrue("the private copy lives in the app's own cache", outcome.document.localFile.absolutePath.startsWith(context.cacheDir.absolutePath))
    }

    @Test
    fun invoicePdfsKeepTheirExistingBehaviour() {
        val outcome = download(downloader({ pdfConnection(it) })) as DownloadOutcome.Success
        assertFalse(outcome.document.isPrivate)
    }
}
