package com.hfremovalsadelaide.invoices.data

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.auth.SessionUser
import com.hfremovalsadelaide.invoices.auth.UserRole
import com.hfremovalsadelaide.invoices.settings.ThemeMode
import com.hfremovalsadelaide.invoices.ui.PlatformUiState
import com.hfremovalsadelaide.invoices.ui.RoleUiPolicy
import com.hfremovalsadelaide.invoices.ui.pricing.PricingAdminContent
import com.hfremovalsadelaide.invoices.ui.pricing.PricingTab
import com.hfremovalsadelaide.invoices.ui.theme.HFRemovalsTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PricingAdminTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun jsonArrayToList(arr: JSONArray): List<String> =
        List(arr.length()) { idx -> arr.getString(idx) }

    @Test
    fun `pricing_api_contract matches Android enums and policy keys`() {
        val raw = javaClass.classLoader!!.getResource("pricing_api_contract.json")!!.readText()
        val json = JSONObject(raw)

        assertEquals(jsonArrayToList(json.getJSONArray("segments")), PricingSegment.entries.map { it.key })
        assertEquals(jsonArrayToList(json.getJSONArray("policyKeys")), PricingPolicyKey.entries.map { it.key })
        assertEquals(jsonArrayToList(json.getJSONArray("serviceUnits")), ServiceUnit.entries.map { it.key })
        assertEquals(jsonArrayToList(json.getJSONArray("gstTreatments")), GstTreatment.entries.map { it.key })
    }

    @Test
    fun `partitionPolicies separates active, drafts, superseded, and future versions cleanly`() {
        val records = listOf(
            PricingPolicyRecord(
                id = "p-old",
                segment = "residential",
                key = "minimum_hours",
                value = "2",
                effectiveFrom = "2026-01-01",
                status = "approved",
                approvedAt = "2026-01-01T00:00:00Z"
            ),
            PricingPolicyRecord(
                id = "p-cur",
                segment = "residential",
                key = "minimum_hours",
                value = "3",
                effectiveFrom = "2026-06-01",
                status = "approved",
                approvedAt = "2026-06-01T00:00:00Z"
            ),
            PricingPolicyRecord(
                id = "p-future",
                segment = "residential",
                key = "minimum_hours",
                value = "3.5",
                effectiveFrom = "2027-01-01",
                status = "approved",
                approvedAt = "2026-11-01T00:00:00Z"
            ),
            PricingPolicyRecord(
                id = "p-draft",
                segment = "residential",
                key = "call_out_fee",
                value = "65.00",
                effectiveFrom = "2026-12-01",
                status = "draft"
            ),
            PricingPolicyRecord(
                id = "p-commercial",
                segment = "commercial",
                key = "minimum_hours",
                value = "4",
                effectiveFrom = "2026-01-01",
                status = "approved"
            )
        )

        val partitioned = PricingAdminLogic.partitionPolicies(records, "residential", "2026-11-27")
        assertEquals("p-cur", partitioned.activeByKey["minimum_hours"]?.id)
        assertEquals(listOf("p-draft"), partitioned.pendingDrafts.map { it.id })
        assertEquals(listOf("p-future", "p-old"), partitioned.historicalAndFuture.map { it.id })
    }

    @Test
    fun `formatHalfHourRate reproduces verified residential 30-min benchmarks`() {
        assertEquals("69.00", PricingAdminLogic.formatHalfHourRate("138"))
        assertEquals("74.00", PricingAdminLogic.formatHalfHourRate("148.00"))
        assertEquals("79.00", PricingAdminLogic.formatHalfHourRate("158"))
        assertEquals("99.00", PricingAdminLogic.formatHalfHourRate("198"))
        assertNull(PricingAdminLogic.formatHalfHourRate("-10"))
        assertNull(PricingAdminLogic.formatHalfHourRate(""))
    }

    @Test
    fun `servicePricingStatus distinguishes unconfigured from explicit approved zero`() {
        assertEquals(ServicePricingStatus.UNCONFIGURED, PricingAdminLogic.servicePricingStatus(null, null))
        assertEquals(ServicePricingStatus.UNCONFIGURED, PricingAdminLogic.servicePricingStatus("", GstTreatment.INCLUSIVE))
        assertEquals(ServicePricingStatus.UNCONFIGURED, PricingAdminLogic.servicePricingStatus("0.00", null))
        assertEquals(ServicePricingStatus.ZERO, PricingAdminLogic.servicePricingStatus("0", GstTreatment.NONE))
        assertEquals(ServicePricingStatus.ZERO, PricingAdminLogic.servicePricingStatus("0.00", GstTreatment.INCLUSIVE))
        assertEquals(ServicePricingStatus.PRICED, PricingAdminLogic.servicePricingStatus("45.00", GstTreatment.EXCLUSIVE))
    }

    @Test
    fun `filterServices filters by search, move type, unit, pricing status, and history`() {
        val items = listOf(
            ServiceCatalogueItem("1", "Monitor packing", "Protective bubble sleeve", "commercial", ServiceUnit.PER_ITEM, null, null, "2026-01-01", null, true),
            ServiceCatalogueItem("2", "Pool table", "Slate disassembly", "residential", ServiceUnit.FIXED, "350.00", GstTreatment.INCLUSIVE, "2026-01-01", null, true),
            ServiceCatalogueItem("3", "Free tape roll", "Included with packing", null, ServiceUnit.PER_ITEM, "0.00", GstTreatment.NONE, "2026-01-01", null, true),
            ServiceCatalogueItem("4", "Old crate fee", "Retired", null, ServiceUnit.PER_DAY, "15.00", GstTreatment.EXCLUSIVE, "2025-01-01", "2026-01-01", false)
        )

        assertEquals(listOf("1", "2", "3"), PricingAdminLogic.filterServices(items, "", null, null, null, false).map { it.id })
        assertEquals(listOf("1"), PricingAdminLogic.filterServices(items, "bubble", null, null, null, false).map { it.id })
        assertEquals(listOf("3"), PricingAdminLogic.filterServices(items, "", "shared", null, ServicePricingStatus.ZERO, false).map { it.id })
        assertEquals(listOf("4"), PricingAdminLogic.filterServices(items, "", null, ServiceUnit.PER_DAY, null, true).map { it.id })
    }

    @Test
    fun `validatePolicyValue enforces backend constraints`() {
        assertNull(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.RATES_INCLUDE_GST, "true"))
        assertTrue(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.RATES_INCLUDE_GST, "yes") != null)

        assertNull(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.BILLING_INCREMENT_MINUTES, "30"))
        assertTrue(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.BILLING_INCREMENT_MINUTES, "0") != null)
        assertTrue(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.BILLING_INCREMENT_MINUTES, "241") != null)

        assertNull(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.MINIMUM_HOURS, "2.5"))
        assertTrue(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.STANDARD_DAY_HOURS, "0") != null)
        assertNull(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.QUOTE_VALIDITY_DAYS, "14"))
        assertNull(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.CANCELLATION_FEE, "0.00"))
        assertTrue(PricingAdminLogic.validatePolicyValue(PricingPolicyKey.DEPOSIT_PERCENT, "101") != null)
    }

    @Test
    fun `PricingRepository parses and builds payloads accurately`() {
        val policies = PricingRepository.parsePolicies(
            """{"policies":[{"id":"pol-1","segment":"residential","key":"rates_include_gst","value":"false","effective_from":"2026-11-01","status":"approved","notes":"Standard","supersedes_id":null,"created_by":"u1","created_at":"2026-11-01T00:00:00Z","approved_by":"owner-1","approved_at":"2026-11-01T01:00:00Z"}]}"""
        )
        assertEquals(1, policies.size)
        assertEquals("rates_include_gst", policies.single().key)
        assertNull(policies.single().supersedesId)

        val services = PricingRepository.parseServices(
            """{"services":[{"id":"srv-1","name":"Monitor packing","description":"IT gear","segment":null,"unit":"per_item","rate":null,"gst_treatment":null,"effective_from":"2026-11-01","effective_to":null,"active":true,"supersedes_id":null}]}"""
        )
        assertEquals(ServiceUnit.PER_ITEM, services.single().unit)
        assertNull(services.single().rate)
        assertNull(services.single().gstTreatment)

        val saveSvc = PricingRepository.buildSaveServicePayload(null, "Mac Mini packing", "IT", null, ServiceUnit.PER_ITEM, null, null, "2026-11-27", true)
        assertEquals(JSONObject.NULL, saveSvc.get("rate"))
        assertEquals(JSONObject.NULL, saveSvc.get("gstTreatment"))

        val draft = PricingRepository.buildSavePolicyDraftPayload("interstate", "minimum_hours", "4", "2026-12-01", "Interstate floor")
        assertEquals("save", draft.getString("action"))
        assertEquals("interstate", draft.getString("segment"))
    }

    @Test
    fun `RoleUiPolicy gates pricing view and owner-only approvals`() {
        val owner = SessionUser("1", "Owner", "o@hf.test", UserRole.OWNER, false, setOf("settings.manage", "invoices.read"))
        val admin = SessionUser("2", "Admin", "a@hf.test", UserRole.ADMIN, false, setOf("settings.manage", "invoices.read"))
        val dispatcher = SessionUser("3", "Disp", "d@hf.test", UserRole.DISPATCHER, false, setOf("jobs.assign"))

        assertTrue(RoleUiPolicy.canViewPricingAdmin(owner))
        assertTrue(RoleUiPolicy.canApprovePricing(owner))

        assertTrue(RoleUiPolicy.canViewPricingAdmin(admin))
        assertFalse(RoleUiPolicy.canApprovePricing(admin))

        assertFalse(RoleUiPolicy.canViewPricingAdmin(dispatcher))
        assertFalse(RoleUiPolicy.canApprovePricing(dispatcher))
    }

    @Test
    fun `PricingAdminContent enforces owner approval UI and distinguishes unconfigured vs zero services`() {
        var tab by mutableStateOf(PricingTab.POLICIES)
        var approvedId: String? = null
        val state = PlatformUiState(
            checkingSession = false,
            pricingLoaded = true,
            pricingPolicies = listOf(
                PricingPolicyRecord("pol-draft-1", "residential", "call_out_fee", "55.00", "2026-12-01", "draft", "Peak fee")
            ),
            rateCards = listOf(
                RateCard("rc-1", "Small 8t + 2 movers", "residential", "8t", 2, "138.00", "2", "0", "2026-01-01", true, overtimeRate = null)
            ),
            serviceCatalogue = listOf(
                ServiceCatalogueItem("s-unconf", "Pool tables", "Slate relocation", "residential", ServiceUnit.FIXED, null, null, "2026-01-01", null, true),
                ServiceCatalogueItem("s-zero", "Standard boxes loan", "Complimentary boxes", null, ServiceUnit.PER_ITEM, "0.00", GstTreatment.NONE, "2026-01-01", null, true)
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.4f)) {
                HFRemovalsTheme(themeMode = ThemeMode.DARK) {
                    Box(Modifier.width(320.dp)) {
                        PricingAdminContent(
                            state = state,
                            isOwner = true,
                            today = "2026-11-27",
                            selectedTab = tab,
                            onSelectTab = { tab = it },
                            selectedSegment = PricingSegment.RESIDENTIAL,
                            onSelectSegment = {},
                            onRefresh = {},
                            onSavePolicyDraft = { _, _, _, _, _ -> },
                            onApprovePolicy = { approvedId = it },
                            onSaveRateCard = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                            onSaveService = { _, _, _, _, _, _, _, _, _ -> }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("pricing_admin_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("approve_policy_call_out_fee").performClick()
        assertEquals("pol-draft-1", approvedId)

        composeRule.onNodeWithTag("pricing_tab_rate_cards").performClick()
        composeRule.onNodeWithTag("verified_benchmark_69.00").assertIsDisplayed()

        composeRule.onNodeWithTag("pricing_tab_catalogue").performClick()
        composeRule.onNodeWithTag("service_search_input").performTextInput("Pool")
        composeRule.onNodeWithTag("pricing_admin_screen").performScrollToNode(hasTestTag("service_card_Pool tables"))
        composeRule.onNodeWithTag("service_card_Pool tables").assertIsDisplayed()
        composeRule.onNodeWithText("Unconfigured (no charge set)").assertIsDisplayed()
    }
}
