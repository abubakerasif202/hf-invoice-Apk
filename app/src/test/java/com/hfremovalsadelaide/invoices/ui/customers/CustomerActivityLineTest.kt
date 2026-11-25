package com.hfremovalsadelaide.invoices.ui.customers

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerActivityLineTest {
    @Test
    fun `loaded counts use the right singular and plural`() {
        assertEquals("1 job · 1 invoice · Business", customerActivityLine(1, 1, "Business"))
        assertEquals("2 jobs · 3 invoices · Residential", customerActivityLine(2, 3, "Residential"))
        assertEquals("0 jobs · 0 invoices · Residential", customerActivityLine(0, 0, "Residential"))
    }

    @Test
    fun `counts that have not loaded are omitted rather than shown as zero`() {
        assertEquals("1 job · Business", customerActivityLine(1, null, "Business"))
        assertEquals("2 invoices · Business", customerActivityLine(null, 2, "Business"))
        assertEquals("Business", customerActivityLine(null, null, "Business"))
    }
}
