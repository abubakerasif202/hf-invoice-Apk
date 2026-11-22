package com.hfremovalsadelaide.invoices.security

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrustedOriginPolicyTest {

    @Test
    fun allowsProductionHttpsOrigin() {
        val uri = Uri.parse("https://hf-invoice-generator.vercel.app/")
        assertTrue(TrustedOriginPolicy.isTrustedOrigin(uri))
        assertEquals(
            TrustedOriginPolicy.NavigationDecision.AllowInWebView,
            TrustedOriginPolicy.classifyNavigation(uri)
        )
    }

    @Test
    fun blocksCleartextHttpOnProductionHost() {
        val uri = Uri.parse("http://hf-invoice-generator.vercel.app/")
        assertFalse(TrustedOriginPolicy.isTrustedOrigin(uri))
        assertEquals(
            TrustedOriginPolicy.NavigationDecision.Block,
            TrustedOriginPolicy.classifyNavigation(uri)
        )
    }

    @Test
    fun blocksDangerousSchemes() {
        val schemes = listOf(
            "file:///etc/passwd",
            "javascript:alert(1)",
            "content://com.example/data",
            "intent://scan/#Intent;end"
        )
        schemes.forEach { raw ->
            assertEquals(
                "Expected $raw to be blocked",
                TrustedOriginPolicy.NavigationDecision.Block,
                TrustedOriginPolicy.classifyNavigation(Uri.parse(raw))
            )
        }
    }

    @Test
    fun rejectsLookalikeHostsAndParserDifferentials() {
        val hostile = listOf(
            "https://hf-invoice-generator.vercel.app.evil.com/",
            "https://evilhf-invoice-generator.vercel.app/",
            "https://hf-invoice-generator.vercel.app@evil.com/",
            "https://user:pw@hf-invoice-generator.vercel.app/",
            "https://evil.com\\@hf-invoice-generator.vercel.app/",
            "https://evil.com\\.hf-invoice-generator.vercel.app/",
            "https://hf-invoice-generator.vercel.app:8443/",
            "https://hf-invoice-generator.vercel.app\t.evil.com/",
            "HTTPS://HF-INVOICE-GENERATOR.VERCEL.APP.evil.com/"
        )
        hostile.forEach { raw ->
            assertFalse("Expected $raw untrusted", TrustedOriginPolicy.isTrustedOrigin(raw))
            assertFalse(
                "Expected $raw untrusted as Uri",
                TrustedOriginPolicy.isTrustedOrigin(Uri.parse(raw))
            )
            assertFalse(
                "Expected $raw never to load in the WebView",
                TrustedOriginPolicy.classifyNavigation(Uri.parse(raw)) ==
                    TrustedOriginPolicy.NavigationDecision.AllowInWebView
            )
        }
    }

    @Test
    fun acceptsCaseInsensitiveHostAndExplicitDefaultPort() {
        assertTrue(TrustedOriginPolicy.isTrustedOrigin("HTTPS://HF-Invoice-Generator.Vercel.App/dashboard"))
        assertTrue(TrustedOriginPolicy.isTrustedOrigin("https://hf-invoice-generator.vercel.app:443/dashboard"))
    }

    @Test
    fun pdfRouteRejectsTraversalAndNestedPaths() {
        listOf(
            "https://hf-invoice-generator.vercel.app/api/pdf/",
            "https://hf-invoice-generator.vercel.app/api/pdf/../admin",
            "https://hf-invoice-generator.vercel.app/api/pdf/%2e%2e/admin",
            "https://hf-invoice-generator.vercel.app/api/pdfx/123"
        ).forEach { raw ->
            assertFalse(
                "Expected $raw not to be a PDF download",
                TrustedOriginPolicy.isPdfDownloadUrl(Uri.parse(raw))
            )
        }
    }

    @Test
    fun routesUnrelatedHttpsLinksExternally() {
        val uri = Uri.parse("https://www.ato.gov.au/businesses-and-organisations")
        val decision = TrustedOriginPolicy.classifyNavigation(uri)
        assertTrue(decision is TrustedOriginPolicy.NavigationDecision.OpenExternally)
        assertEquals(uri, (decision as TrustedOriginPolicy.NavigationDecision.OpenExternally).uri)
    }

    @Test
    fun routesMailtoAndTelExternally() {
        val mail = Uri.parse("mailto:admin@hfremovalsadelaide.com.au")
        val tel = Uri.parse("tel:+61400000000")
        assertTrue(TrustedOriginPolicy.classifyNavigation(mail) is TrustedOriginPolicy.NavigationDecision.OpenExternally)
        assertTrue(TrustedOriginPolicy.classifyNavigation(tel) is TrustedOriginPolicy.NavigationDecision.OpenExternally)
    }

    @Test
    fun classifiesPdfDownloadAndInlinePrintRequests() {
        val downloadUri = Uri.parse("https://hf-invoice-generator.vercel.app/api/pdf/123e4567-e89b-12d3-a456-426614174000")
        val printUri = Uri.parse("https://hf-invoice-generator.vercel.app/api/pdf/123e4567-e89b-12d3-a456-426614174000?inline")

        val downloadDecision = TrustedOriginPolicy.classifyNavigation(downloadUri)
        assertEquals(
            TrustedOriginPolicy.NavigationDecision.DownloadPdf(downloadUri.toString(), false),
            downloadDecision
        )

        val printDecision = TrustedOriginPolicy.classifyNavigation(printUri)
        assertEquals(
            TrustedOriginPolicy.NavigationDecision.DownloadPdf(printUri.toString(), true),
            printDecision
        )
    }

    @Test
    fun classifiesCsvExportRequest() {
        val exportUri = Uri.parse("https://hf-invoice-generator.vercel.app/api/export")
        assertEquals(
            TrustedOriginPolicy.NavigationDecision.DownloadCsv(exportUri.toString()),
            TrustedOriginPolicy.classifyNavigation(exportUri)
        )
    }

    @Test
    fun extractsAndSanitizesOriginalInvoiceFilenames() {
        val standard = TrustedOriginPolicy.extractAndSanitizeFilename(
            contentDisposition = """attachment; filename="HF-Invoice-INV-2026-0042.pdf"""",
            fallbackName = "fallback.pdf",
            requiredExtension = ".pdf"
        )
        assertEquals("HF-Invoice-INV-2026-0042.pdf", standard)

        val traversal = TrustedOriginPolicy.extractAndSanitizeFilename(
            contentDisposition = """attachment; filename="../../evil/HF-Invoice-01.pdf"""",
            fallbackName = "fallback.pdf",
            requiredExtension = ".pdf"
        )
        assertFalse(traversal.contains("/"))
        assertFalse(traversal.contains("\\"))
        assertTrue(traversal.endsWith(".pdf"))

        val rfc5987 = TrustedOriginPolicy.extractAndSanitizeFilename(
            contentDisposition = """inline; filename*=UTF-8''HF-Invoice%20007.pdf""",
            fallbackName = "fallback.pdf",
            requiredExtension = ".pdf"
        )
        assertEquals("HF-Invoice 007.pdf", rfc5987)
    }

    @Test
    fun restoredUrlIsRevalidatedAndFallsBackToTheStartPage() {
        val start = TrustedOriginPolicy.PRODUCTION_URL
        assertEquals(start, TrustedOriginPolicy.restorableUrl(null))
        assertEquals(start, TrustedOriginPolicy.restorableUrl(""))
        assertEquals(start, TrustedOriginPolicy.restorableUrl("http://hf-invoice-generator.vercel.app/dashboard"))
        assertEquals(start, TrustedOriginPolicy.restorableUrl("https://evil.example/dashboard"))
        assertEquals(start, TrustedOriginPolicy.restorableUrl("https://hf-invoice-generator.vercel.app.evil.com/"))
        assertEquals(start, TrustedOriginPolicy.restorableUrl("javascript:alert(1)"))
        // API/download routes are never a page to come back to.
        assertEquals(start, TrustedOriginPolicy.restorableUrl("https://hf-invoice-generator.vercel.app/api/pdf/abc"))
        assertEquals(start, TrustedOriginPolicy.restorableUrl("https://hf-invoice-generator.vercel.app/api/export"))
        assertEquals(start, TrustedOriginPolicy.restorableUrl("https://hf-invoice-generator.vercel.app/api/manage"))
    }

    @Test
    fun restoredUrlKeepsATrustedPageTheUserWasOn() {
        val page = "https://hf-invoice-generator.vercel.app/customers/42?tab=invoices"
        assertEquals(page, TrustedOriginPolicy.restorableUrl(page))
    }}
