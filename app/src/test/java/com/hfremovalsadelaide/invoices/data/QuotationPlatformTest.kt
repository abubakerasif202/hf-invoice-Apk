package com.hfremovalsadelaide.invoices.data

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.offline.CipherBox
import com.hfremovalsadelaide.invoices.offline.EncryptedStore
import com.hfremovalsadelaide.invoices.settings.ThemeMode
import com.hfremovalsadelaide.invoices.ui.customers.QuotationWizardScreen
import com.hfremovalsadelaide.invoices.ui.theme.HFRemovalsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuotationPlatformTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testCipherBox() = object : CipherBox {
        override fun encrypt(plainText: String): ByteArray =
            plainText.toByteArray(StandardCharsets.UTF_8).reversedArray()

        override fun decrypt(cipherBlob: ByteArray): String? =
            String(cipherBlob.reversedArray(), StandardCharsets.UTF_8)
    }

    @Test
    fun `QuoteDraftStore persists encrypted drafts and isolates accounts cleanly`() {
        val ctx = RuntimeEnvironment.getApplication()
        val store = EncryptedStore(ctx, testCipherBox())
        store.clearAll()
        val draftStore = QuoteDraftStore(store)

        val draftA = QuoteWizardDraft(
            stepIndex = 2,
            customerName = "Account A Buyer",
            segment = "commercial",
            pickup = "1 King William St",
            delivery = "9 Pirie St",
            pricingMode = "fixed_price",
            fixedPrice = "1400.00"
        )
        draftStore.saveDraft("acc-a", draftA)

        val loadedA = draftStore.loadDraft("acc-a")
        assertNotNull(loadedA)
        assertEquals("Account A Buyer", loadedA?.customerName)
        assertEquals("commercial", loadedA?.segment)
        assertEquals("fixed_price", loadedA?.pricingMode)
        assertEquals("1400.00", loadedA?.fixedPrice)

        // Different account sees nothing; clearing acc-a clears only acc-a.
        assertNull(draftStore.loadDraft("acc-b"))
        draftStore.saveDraft("acc-b", QuoteWizardDraft(customerName = "Account B Buyer"))
        draftStore.clearDraft("acc-a")
        assertNull(draftStore.loadDraft("acc-a"))
        assertEquals("Account B Buyer", draftStore.loadDraft("acc-b")?.customerName)
        store.clearAll()
    }

    @Test
    fun `QuotationRepository builds server-only pricing payloads without client rate injection`() {
        val draft = QuoteWizardDraft(
            customerName = "Test Customer",
            segment = "interstate",
            preferredDate = "2026-12-01",
            extraDates = listOf("2026-12-02"),
            pickup = "Adelaide SA",
            delivery = "Melbourne VIC",
            pricingMode = "hourly_estimate",
            serviceDays = listOf(
                QuoteServiceDayDraft(date = "2026-12-01", rateCardId = "card-uuid-1", rateUnit = "half_hour", movers = 2, estimatedHours = "6"),
                QuoteServiceDayDraft(date = "2026-12-02", rateCardId = null, manualRate = "210.00", manualRateReason = "Linehaul day", rateUnit = "hourly", movers = 3, estimatedHours = "8")
            ),
            selectedServices = listOf(QuoteSelectedServiceDraft("svc-uuid-1", "Packing boxes", ServiceUnit.PER_ITEM, "20")),
            discountEnabled = true,
            discountType = "percent",
            discountValue = "5",
            discountReason = "Loyalty"
        )
        val priceReq = QuotationRepository.buildPriceRequestPayload(draft, "2026-11-27")
        assertEquals("interstate", priceReq.getString("segment"))
        val days = priceReq.getJSONArray("serviceDays")
        assertEquals(2, days.length())
        assertEquals("card-uuid-1", days.getJSONObject(0).getString("rateCardId"))
        assertTrue(days.getJSONObject(0).isNull("manualRate"))
        assertEquals("210.00", days.getJSONObject(1).getString("manualRate"))
        assertEquals("Linehaul day", days.getJSONObject(1).getString("manualRateReason"))

        val parsedResult = QuotationRepository.parsePriceResult(
            """{"segment":"interstate","pricingMode":"hourly_estimate","isEstimate":true,"lines":[],"subtotal":"1000.00","discount":"50.00","gst":"95.00","total":"1045.00","depositRequested":"104.50","depositReceived":"50.00","balanceEstimated":"995.00","validUntil":"2026-12-15","terms":{"quoteValidityDays":"14","cancellationFee":"50.00"},"issues":[],"issuable":true}"""
        )
        assertTrue(parsedResult.issuable)
        assertEquals("1045.00", parsedResult.total)
        assertEquals("995.00", parsedResult.balanceEstimated)
    }

    @Test
    fun `QuotationWizardScreen blocks issuing when pricing or terms incomplete and enables when valid on narrow screen`() {
        val blockedDraft = QuoteWizardDraft(
            stepIndex = 4,
            customerName = "Alex Test",
            segment = "residential",
            preferredDate = "2026-12-01",
            serviceDays = listOf(QuoteServiceDayDraft(date = "2026-12-01"))
        )
        val blockedPrice = QuotePriceResult(
            segment = "residential",
            pricingMode = "hourly_estimate",
            isEstimate = true,
            lines = emptyList(),
            subtotal = "0.00",
            discount = "0.00",
            gst = "0.00",
            total = "0.00",
            depositRequested = null,
            depositReceived = "0.00",
            balanceEstimated = "0.00",
            validUntil = null,
            quoteValidityDays = null,
            cancellationFee = null,
            issues = listOf("minimum_hours not configured for residential"),
            issuable = false
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.4f)) {
                HFRemovalsTheme(themeMode = ThemeMode.DARK) {
                    Box(Modifier.width(320.dp)) {
                        QuotationWizardScreen(
                            draft = blockedDraft,
                            customers = emptyList(),
                            allJobs = emptyList(),
                            allInvoices = emptyList(),
                            allQuotes = emptyList(),
                            rateCards = emptyList(),
                            catalogue = emptyList(),
                            termsVersions = emptyList(),
                            duplicateMatches = emptyList(),
                            priceResult = blockedPrice,
                            pricingBusy = false,
                            loadingModule = false,
                            onUpdateDraft = {},
                            onCheckDuplicates = { _, _, _ -> },
                            onRefreshServerPrice = {},
                            onSaveDraftToServer = {},
                            onPreviewPdf = {},
                            onIssueQuotation = { _, _ -> },
                            onClose = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("quotation_wizard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_persistent_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_blocker_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_issue_btn").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `QuotationWizardScreen enables issuing when server pricing and approved terms are complete`() {
        var issuedCalled = false
        val readyDraft = QuoteWizardDraft(
            stepIndex = 4,
            customerName = "Ready Customer",
            segment = "commercial",
            preferredDate = "2026-12-05",
            termsVersionId = "term-1",
            serviceDays = listOf(QuoteServiceDayDraft(date = "2026-12-05"))
        )
        val readyPrice = QuotePriceResult(
            segment = "commercial",
            pricingMode = "fixed_price",
            isEstimate = false,
            lines = emptyList(),
            subtotal = "1500.00",
            discount = "0.00",
            gst = "150.00",
            total = "1650.00",
            depositRequested = "165.00",
            depositReceived = "0.00",
            balanceEstimated = "1650.00",
            validUntil = "2026-12-19",
            quoteValidityDays = "14",
            cancellationFee = "50.00",
            issues = emptyList(),
            issuable = true
        )
        val terms = listOf(
            TermsVersionRecord("term-1", "commercial", "Commercial Terms", "Payment within 14 days.", 1, "2026-01-01", null, "approved", null)
        )

        composeRule.setContent {
            HFRemovalsTheme {
                QuotationWizardScreen(
                    draft = readyDraft,
                    customers = emptyList(),
                    allJobs = emptyList(),
                    allInvoices = emptyList(),
                    allQuotes = emptyList(),
                    rateCards = emptyList(),
                    catalogue = emptyList(),
                    termsVersions = terms,
                    duplicateMatches = emptyList(),
                    priceResult = readyPrice,
                    pricingBusy = false,
                    loadingModule = false,
                    onUpdateDraft = {},
                    onCheckDuplicates = { _, _, _ -> },
                    onRefreshServerPrice = {},
                    onSaveDraftToServer = {},
                    onPreviewPdf = {},
                    onIssueQuotation = { _, _ -> issuedCalled = true },
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag("wizard_issue_btn").performScrollTo().assertIsEnabled().performClick()
        assertTrue(issuedCalled)
    }
}
