package com.hfremovalsadelaide.invoices.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QrCodeToolTest {

    @Test
    fun `generates QR bitmap and decodes exact carton barcode`() {
        val code = "HFR-8821-CARTON-04"
        val bmp = QrCodeTool.generateQrBitmap(code, sizePx = 280)
        assertNotNull(bmp)
        assertEquals(280, bmp!!.width)
        assertEquals(280, bmp.height)
        assertEquals(code, QrCodeTool.decodeFromBitmap(bmp))
    }
}
