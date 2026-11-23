package com.hfremovalsadelaide.invoices.jobs

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignatureRendererTest {
    @Test
    fun `blank or single-dot signatures produce nothing`() {
        assertFalse(SignatureRenderer.hasInk(emptyList()))
        assertFalse(SignatureRenderer.hasInk(listOf(listOf(1f to 1f))))
        assertEquals("", SignatureRenderer.toPngBase64(emptyList(), 300f, 120f))
    }

    @Test
    fun `drawn strokes render to a PNG the backend will accept`() {
        val strokes = listOf(listOf(10f to 10f, 80f to 60f, 150f to 20f), listOf(20f to 90f, 200f to 95f))
        val png = Base64.decode(SignatureRenderer.toPngBase64(strokes, 300f, 120f), Base64.NO_WRAP)
        assertEquals(0x89.toByte(), png[0])
        assertEquals("PNG", String(png, 1, 3))
        assertTrue("backend rejects images under 100 bytes", png.size > 100)
        assertTrue("backend rejects images over 300 KB", png.size < 300_000)
    }
}
