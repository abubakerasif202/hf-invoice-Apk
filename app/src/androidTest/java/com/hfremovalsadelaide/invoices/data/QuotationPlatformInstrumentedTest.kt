package com.hfremovalsadelaide.invoices.data

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hfremovalsadelaide.invoices.offline.EncryptedStore
import com.hfremovalsadelaide.invoices.settings.ThemeMode
import com.hfremovalsadelaide.invoices.ui.customers.QuotationWizardScreen
import com.hfremovalsadelaide.invoices.ui.theme.HFRemovalsTheme
import com.hfremovalsadelaide.invoices.ui.components.HfSectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import android.graphics.Bitmap
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuotationPlatformInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionHeadingHasReadableContrastInDarkTheme() {
        var background = Color.Unspecified
        composeRule.setContent {
            HFRemovalsTheme(themeMode = ThemeMode.DARK) {
                background = MaterialTheme.colorScheme.surface
                HfSectionCard(title = "Synthetic section heading") {}
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("Synthetic section heading").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foregroundLuminance = layouts.single().layoutInput.style.color.luminance()
        val backgroundLuminance = background.luminance()
        val contrast = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05) / (minOf(foregroundLuminance, backgroundLuminance) + 0.05)
        assertTrue("Small section heading contrast must be at least 4.5:1; was $contrast", contrast >= 4.5)
    }

    @Test
    fun wizardNavigatesAllFiveStepsAndPreservesTypedCustomerWhenClosed() {
        val draft = mutableStateOf(QuoteWizardDraft())
        var discardRequested: Boolean? = null
        composeRule.setContent {
            HFRemovalsTheme {
                QuotationWizardScreen(
                    draft = draft.value,
                    customers = emptyList(), allJobs = emptyList(), allInvoices = emptyList(),
                    allQuotes = emptyList(), rateCards = emptyList(), catalogue = emptyList(),
                    termsVersions = emptyList(), duplicateMatches = emptyList(),
                    priceResult = null, pricingBusy = false, loadingModule = false,
                    onUpdateDraft = { draft.value = it(draft.value) },
                    onCheckDuplicates = { _, _, _ -> }, onRefreshServerPrice = {},
                    onSaveDraftToServer = {}, onPreviewPdf = {}, onIssueQuotation = { _, _ -> },
                    onClose = { discardRequested = it }
                )
            }
        }
        composeRule.onNodeWithTag("wizard_customer_name").performScrollTo().performTextInput("Synthetic Device Customer")
        for (step in 1..4) {
            composeRule.onNodeWithTag("wizard_next_btn").performClick()
            composeRule.runOnIdle { assertEquals(step, draft.value.stepIndex) }
        }
        composeRule.onNodeWithTag("wizard_step_chip_0").performClick()
        composeRule.runOnIdle { assertEquals("Synthetic Device Customer", draft.value.customerName) }
        composeRule.onNodeWithContentDescription("Close wizard").performClick()
        composeRule.runOnIdle { assertEquals(false, discardRequested) }
    }

    private fun captureScreen(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "visual-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test
    fun encryptedDraftSurvivesStoreRecreationAndRemainsIsolatedByAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountA = "device-test-a-${UUID.randomUUID()}"
        val accountB = "device-test-b-${UUID.randomUUID()}"
        val drafts = QuoteDraftStore(EncryptedStore(context))
        try {
            drafts.saveDraft(accountA, QuoteWizardDraft(stepIndex = 2, customerName = "Synthetic Account A"))
            drafts.saveDraft(accountB, QuoteWizardDraft(stepIndex = 4, customerName = "Synthetic Account B"))
            val encrypted = File(context.filesDir, "hf_secure_cache/quote_wizard_draft_$accountA.enc").readBytes()
            assertFalse(encrypted.toString(Charsets.UTF_8).contains("Synthetic Account A"))

            val restored = QuoteDraftStore(EncryptedStore(context))
            assertEquals(2, restored.loadDraft(accountA)?.stepIndex)
            assertEquals("Synthetic Account A", restored.loadDraft(accountA)?.customerName)
            assertEquals("Synthetic Account B", restored.loadDraft(accountB)?.customerName)
            assertNull(restored.loadDraft("device-test-absent-${UUID.randomUUID()}"))

            restored.clearDraft(accountA)
            assertNull(restored.loadDraft(accountA))
            assertEquals("Synthetic Account B", restored.loadDraft(accountB)?.customerName)
        } finally {
            drafts.clearDraft(accountA)
            drafts.clearDraft(accountB)
        }
    }
    @Test
    fun narrowDarkReviewBlocksIssuingWithoutConfiguredPricingAndTerms() {
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
        captureScreen("quotation-narrow-dark-blocked")
    }

    @Test
    fun reviewIssuesOnlyAfterServerPricingAndApprovedTermsAreComplete() {
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
        captureScreen("quotation-ready-review")
    }
}
