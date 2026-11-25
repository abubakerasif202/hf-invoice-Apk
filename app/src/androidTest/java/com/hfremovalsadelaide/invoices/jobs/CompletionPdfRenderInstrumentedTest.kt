package com.hfremovalsadelaide.invoices.jobs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders a synthetic completion PDF (produced by the real backend code from fake data) with Android's own
 * PdfRenderer, saves the page as a PNG for visual review, and checks the logo and signature are really drawn.
 */
@RunWith(AndroidJUnit4::class)
class CompletionPdfRenderInstrumentedTest {

    private fun render(): Bitmap {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "completion_fixture.pdf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("completion_fixture.pdf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                assertEquals("completion summary is a single page", 1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val scale = 2
                    val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.getExternalFilesDir(null), "completion-render.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    return bitmap
                }
            }
        }
    }

    private fun coloured(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Int {
        var n = 0
        for (x in (bitmap.width * left).toInt() until (bitmap.width * right).toInt()) {
            for (y in (bitmap.height * top).toInt() until (bitmap.height * bottom).toInt()) {
                val c = bitmap.getPixel(x, y)
                val max = maxOf(Color.red(c), Color.green(c), Color.blue(c))
                val min = minOf(Color.red(c), Color.green(c), Color.blue(c))
                if (max - min > 60) n++ // saturated pixel: HF green, gold or red, not white/grey text
            }
        }
        return n
    }

    @Test
    fun logoIsDrawnInTheHeaderAndTheSignatureAreaIsNotBlank() {
        val bitmap = render()
        assertTrue("A4 portrait", bitmap.height > bitmap.width)
        val logoPixels = coloured(bitmap, 0.05f, 0.02f, 0.30f, 0.15f)
        assertTrue("the HF logo should put plenty of green/gold in the top-left (found $logoPixels)", logoPixels > 3_000)
        var dark = 0
        // The signature block sits in the middle of the page (about 38%-56% of its height).
        for (x in 0 until bitmap.width step 2) for (y in (bitmap.height * 0.38f).toInt() until (bitmap.height * 0.56f).toInt() step 2) {
            if (Color.red(bitmap.getPixel(x, y)) < 90) dark++
        }
        assertTrue("text and signature ink exist in the lower body (found $dark)", dark > 200)
    }
}
