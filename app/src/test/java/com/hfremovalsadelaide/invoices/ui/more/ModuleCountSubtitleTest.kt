package com.hfremovalsadelaide.invoices.ui.more

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleCountSubtitleTest {
    @Test
    fun `a loaded count is shown with its noun`() {
        assertEquals("3 trucks · Rego", moduleCountSubtitle(3, "trucks", "Rego", loading = false))
    }

    @Test
    fun `a genuine zero is still shown once the data has loaded`() {
        assertEquals("0 trucks · Rego", moduleCountSubtitle(0, "trucks", "Rego", loading = false))
    }

    @Test
    fun `unloaded data shows loading while a module is loading, never a zero`() {
        assertEquals("Loading… · Rego", moduleCountSubtitle(null, "trucks", "Rego", loading = true))
    }

    @Test
    fun `unloaded data with nothing loading omits the count instead of showing zero`() {
        assertEquals("Rego", moduleCountSubtitle(null, "trucks", "Rego", loading = false))
    }
}
