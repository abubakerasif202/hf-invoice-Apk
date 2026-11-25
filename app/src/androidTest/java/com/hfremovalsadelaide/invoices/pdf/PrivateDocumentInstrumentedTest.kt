package com.hfremovalsadelaide.invoices.pdf

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hfremovalsadelaide.invoices.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Signed documents stay private; export is an explicit Share/Open. Uses a synthetic file, never real data. */
@RunWith(AndroidJUnit4::class)
class PrivateDocumentInstrumentedTest {

    private fun document(context: Context): DownloadedDocument {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "HF-Job-Completion-TEST.pdf").apply { writeBytes("%PDF-1.7\n".toByteArray() + ByteArray(2_000)) }
        return DownloadedDocument(
            fileName = file.name, mimeType = "application/pdf", localFile = file, sizeBytes = file.length(),
            savedToPublicDownloads = false, isPrivate = true
        )
    }

    @Test
    fun privateDocumentIsLabelledPrivateAndNotInSharedStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val doc = document(context)
        assertTrue(doc.isPrivate)
        assertFalse(doc.savedToPublicDownloads)
        assertTrue(doc.localFile.absolutePath.startsWith(context.cacheDir.absolutePath))
        assertEquals("Kept private in the app. Use Share to export it.", context.getString(R.string.download_kept_private))
    }

    @Test
    fun shareAndOpenUseAScopedFileProviderGrantNotAFilePath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val doc = document(context)
        val chooser = PdfActions.buildShareIntent(context, doc)
        // Sharing goes through the system chooser, so the user picks the destination explicitly.
        assertTrue(chooser.action in setOf(Intent.ACTION_CHOOSER, "com.huawei.intent.action.hwCHOOSER"))
        val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("application/pdf", share.type)
        val uri = share.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        assertEquals("content", uri?.scheme)
        assertEquals("${context.packageName}.fileprovider", uri?.authority)
        assertTrue("read access is granted per share", share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val open = PdfActions.buildOpenIntent(context, doc)
        assertEquals(Intent.ACTION_VIEW, open.action)
        assertTrue(open.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("content", open.data?.scheme)
    }
}
