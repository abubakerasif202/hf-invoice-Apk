package com.hfremovalsadelaide.invoices.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hfremovalsadelaide.invoices.auth.SessionUser
import com.hfremovalsadelaide.invoices.auth.UserRole
import com.hfremovalsadelaide.invoices.data.AnalyticsSnapshot
import com.hfremovalsadelaide.invoices.data.MonthlyTrendPoint
import com.hfremovalsadelaide.invoices.data.TruckUtilItem
import com.hfremovalsadelaide.invoices.jobs.ConditionReportItem
import com.hfremovalsadelaide.invoices.jobs.FakeJobsGateway
import com.hfremovalsadelaide.invoices.jobs.JobAssignment
import com.hfremovalsadelaide.invoices.jobs.JobsViewModel
import com.hfremovalsadelaide.invoices.offline.OfflineOpKind
import com.hfremovalsadelaide.invoices.offline.QueuedOperation
import com.hfremovalsadelaide.invoices.settings.ThemeMode
import com.hfremovalsadelaide.invoices.ui.auth.BrandedSignInScreen
import com.hfremovalsadelaide.invoices.ui.components.SettingsSheet
import com.hfremovalsadelaide.invoices.ui.dashboard.DashboardScreen
import com.hfremovalsadelaide.invoices.ui.jobs.AssignCrewDialog
import com.hfremovalsadelaide.invoices.ui.jobs.JobConditionSheet
import com.hfremovalsadelaide.invoices.ui.jobs.JobInventorySheet
import com.hfremovalsadelaide.invoices.ui.jobs.JobsTab
import com.hfremovalsadelaide.invoices.ui.more.FleetScreen
import com.hfremovalsadelaide.invoices.ui.theme.HFRemovalsTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformUiInstrumentedTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun signInScreenSwitchesModesBetweenStaffAdminInviteAndReset() {
        var submitted: Pair<String, String>? = null
        rule.setContent {
            HFRemovalsTheme(themeMode = ThemeMode.LIGHT) {
                BrandedSignInScreen(
                    busy = false,
                    error = null,
                    onLoginStaff = { email, password -> submitted = email to password },
                    onLoginAdmin = {},
                    onAcceptInvite = { _, _ -> },
                    onResetPassword = { _, _ -> }
                )
            }
        }
        rule.onNodeWithTag("sign_in_card").assertIsDisplayed()
        rule.onNodeWithTag("auth_email").performTextInput("driver@example.invalid")
        rule.onNodeWithTag("auth_password").performTextInput("synthetic-password")
        rule.onNodeWithTag("auth_submit").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("driver@example.invalid" to "synthetic-password", submitted) }
        rule.onNodeWithTag("auth_mode_admin").performClick()
        rule.onNodeWithTag("auth_admin_password").assertIsDisplayed()
        rule.onNodeWithTag("auth_mode_accept_invite").performClick()
        rule.onNodeWithTag("auth_token").assertIsDisplayed()
    }

    @Test
    fun inventorySheetAddsRoomAndRequestsPdf() {
        var room: String? = null
        var exportRequested = false
        rule.setContent {
            HFRemovalsTheme {
                JobInventorySheet(
                    jobId = "fixture-1",
                    jobTitle = "Synthetic move",
                    inventory = null,
                    loading = false,
                    busy = false,
                    onAddRoom = { room = it },
                    onSaveItem = {},
                    onScanBarcode = { _, _, _ -> },
                    onDownloadInventoryPdf = { exportRequested = true },
                    onDismiss = {}
                )
            }
        }
        rule.onNodeWithText("PDF").performClick()
        rule.onNodeWithText("Add Room").performClick()
        rule.onNodeWithText("e.g. Master Bedroom, Server Room").performTextInput("Test Room")
        rule.onNodeWithText("Add").performClick()
        rule.runOnIdle {
            assertEquals("Test Room", room)
            assertTrue(exportRequested)
        }
    }

    @Test
    fun dispatcherCanSubmitANewFleetVehicle() {
        val dispatcher = SessionUser("fixture-dispatch", "Test Dispatcher", "dispatch@example.invalid", UserRole.DISPATCHER, false, setOf("fleet.manage"))
        var submitted: List<String?>? = null
        rule.setContent {
            HFRemovalsTheme {
                FleetScreen(PlatformUiState(user = dispatcher)) { name, rego, vehicleClass, tonnes ->
                    submitted = listOf(name, rego, vehicleClass, tonnes)
                }
            }
        }
        rule.onNodeWithText("Add Vehicle").performClick()
        rule.onNodeWithText("Truck name (e.g. Truck 2 - Hino)").performTextInput("Test Truck")
        rule.onNodeWithText("Registration plate").performTextInput("QA123")
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle { assertEquals(listOf("Test Truck", "QA123", "MR 12t", "12"), submitted) }
    }

    @Test
    fun conditionSheetSavesTheSelectedPhaseAndShowsPrivatePhotoControls() {
        var report: ConditionReportItem? = null
        rule.setContent {
            HFRemovalsTheme {
                JobConditionSheet(
                    job = FakeJobsGateway.fixture(),
                    busy = false,
                    onSaveConditionReport = { report = it },
                    onUploadPhoto = { _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        rule.onNodeWithText("During Move").performClick()
        rule.onNodeWithText("Save During Condition Report").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("during", report?.phase) }
        rule.onNodeWithText("Attach Private Job Photo (0 saved)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Gallery").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dashboardRendersInDarkThemeAndLargeFontScaleWithOfflineQueueBanner() {
        val owner = SessionUser(
            id = "u-owner",
            name = "Abubaker Asif",
            email = "admin@hfremovalsadelaide.com.au",
            role = UserRole.OWNER,
            isLegacyAdmin = true,
            permissions = setOf("reports.read", "jobs.assign", "invoices.manage")
        )
        val analytics = AnalyticsSnapshot(
            jobsToday = 3,
            jobsThisWeek = 12,
            upcomingBookings = 8,
            completedJobs = 24,
            averageJobDurationMinutes = 240,
            crewHours = 96.5,
            revenueInvoicedExclGst = "18400.00",
            gstInvoiced = "1840.00",
            revenueInvoicedInclGst = "20240.00",
            paymentsReceived = "15200.00",
            outstandingBalance = "5040.00",
            overdueInvoices = 1,
            overdueBalance = "1100.00",
            additionalCharges = "420.00",
            customerRepeatRate = 0.35,
            truckUtilisation = listOf(TruckUtilItem("MR 12t", 10, 2400)),
            monthlyTrends = listOf(
                MonthlyTrendPoint("2026-10", "14000.00", "12727.27", "12000.00", 8),
                MonthlyTrendPoint("2026-11", "20240.00", "18400.00", "15200.00", 12)
            )
        )
        val state = PlatformUiState(
            checkingSession = false,
            user = owner,
            isOffline = true,
            queuedOps = listOf(
                QueuedOperation(
                    idempotencyKey = "k-1",
                    kind = OfflineOpKind.JOB_TIME_ACTION,
                    jobId = "j-1",
                    label = "Glenelg Move · start",
                    payload = JSONObject().put("action", "start"),
                    deviceRecordedAt = "2026-11-25T00:00:00Z"
                )
            ),
            analytics = analytics
        )

        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.35f)) {
                HFRemovalsTheme(themeMode = ThemeMode.DARK) {
                    DashboardScreen(
                        platformState = state,
                        jobsSnapshot = null,
                        jobsLoading = false,
                        onRefreshAll = {},
                        onOpenNotifications = {},
                        onOpenSettings = {},
                        onSyncNow = {},
                        onOpenOfflineQueue = {},
                        onOpenJob = {},
                        onNavigateJobsDispatch = {},
                        onNavigateNewInvoice = {},
                        onNavigateAnalytics = {},
                        onNavigateTimesheets = {}
                    )
                }
            }
        }
        rule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        rule.onNodeWithTag("offline_sync_banner").assertIsDisplayed()
        rule.onNodeWithText("$20240.00").assertIsDisplayed()
    }

    @Test
    fun dashboardHeaderKeepsTheFullSubtitleAndActionsVisibleOnANarrowScreen() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.6f)) {
                HFRemovalsTheme {
                    Box(Modifier.width(320.dp)) {
                        DashboardScreen(
                            platformState = PlatformUiState(checkingSession = false),
                            jobsSnapshot = null,
                            jobsLoading = false,
                            onRefreshAll = {},
                            onOpenNotifications = {},
                            onOpenSettings = {},
                            onSyncNow = {},
                            onOpenOfflineQueue = {},
                            onOpenJob = {},
                            onNavigateJobsDispatch = {},
                            onNavigateNewInvoice = {},
                            onNavigateAnalytics = {},
                            onNavigateTimesheets = {}
                        )
                    }
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Operations & Dispatch")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("The subtitle should fit without clipping", layouts.single().hasVisualOverflow)
        rule.onNodeWithContentDescription("Refresh dashboard").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_notifications_btn").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_settings_btn").assertIsDisplayed()
    }

    @Test
    fun jobsTabSupportsDispatchBoardAndSearchFilters() {
        val vm = JobsViewModel(FakeJobsGateway(listOf(FakeJobsGateway.fixture())))
        rule.setContent {
            val state by vm.state.collectAsState()
            HFRemovalsTheme {
                JobsTab(
                    state = state,
                    viewModel = vm,
                    onGoToSignIn = {},
                    onDownloadCompletionPdf = {},
                    permissions = setOf("jobs.read", "jobs.assign", "jobs.reschedule", "calendar.sync"),
                    canDispatch = true
                )
            }
        }
        vm.load()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("jobs_list")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("jobs_list").performScrollToNode(hasTestTag("jobs_search_input"))
        rule.onNodeWithTag("jobs_search_input").assertIsDisplayed()
        rule.onNodeWithTag("jobs_list").performScrollToNode(hasTestTag("jobs_filter_confirmed"))
        rule.onNodeWithTag("jobs_filter_confirmed").assertIsDisplayed()
        rule.onNodeWithTag("jobs_list").performScrollToNode(hasTestTag("jobs_view_dispatch"))
        rule.onNodeWithTag("jobs_view_dispatch").assertIsDisplayed()
    }

    @Test
    fun moverWithAssignedJobsCanOpenDetailsWithoutDispatchOrTimeControls() {
        val vm = JobsViewModel(FakeJobsGateway(listOf(FakeJobsGateway.fixture())))
        rule.setContent {
            val state by vm.state.collectAsState()
            HFRemovalsTheme {
                JobsTab(
                    state = state,
                    viewModel = vm,
                    onGoToSignIn = {},
                    onDownloadCompletionPdf = {},
                    permissions = setOf("jobs.read", "inventory.read", "inventory.write", "condition.write", "timesheets.own")
                )
            }
        }
        vm.load()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("jobs_list")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("jobs_sync_button").assertDoesNotExist()
        rule.onNodeWithTag("jobs_list").performScrollToNode(hasTestTag("job_card_fixture-1"))
        rule.onNodeWithTag("job_card_fixture-1").performClick()
        rule.onNodeWithTag("job_inventory_btn").assertIsDisplayed()
        rule.onNodeWithTag("job_start").assertDoesNotExist()
        rule.onNodeWithTag("job_crew_event_btn").assertDoesNotExist()
    }

    @Test
    fun savingDispatchNotesPreservesCrewWhenStaffDirectoryHasNotLoaded() {
        val job = FakeJobsGateway.fixture().copy(
            staffIds = listOf("staff-1"),
            assignees = listOf("Test Driver", "Manual Mover"),
            assignmentDetails = listOf(
                JobAssignment("staff-1", "Test Driver", "driver"),
                JobAssignment(null, "Manual Mover", "mover")
            )
        )
        var saved = emptyList<Triple<String?, String, String>>()
        rule.setContent {
            HFRemovalsTheme {
                AssignCrewDialog(
                    job = job,
                    staff = emptyList(),
                    vehicles = emptyList(),
                    onDismiss = {},
                    onSave = { crew, _, _, _, _ -> saved = crew }
                )
            }
        }
        rule.onNodeWithText("Save Assignment").performClick()
        assertEquals(
            listOf(Triple("staff-1", "Test Driver", "driver"), Triple(null, "Manual Mover", "mover")),
            saved
        )
    }

    @Test
    fun settingsSheetAllowsSwitchingThemeMode() {
        var selectedTheme: ThemeMode? = null
        rule.setContent {
            HFRemovalsTheme {
                SettingsSheet(
                    privacyModeEnabled = false,
                    onPrivacyModeChange = {},
                    versionName = "2.0.0",
                    onDismiss = {},
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = { selectedTheme = it }
                )
            }
        }
        rule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        rule.onNodeWithTag("theme_mode_dark").performClick()
        assertTrue(selectedTheme == ThemeMode.DARK)
    }
}
