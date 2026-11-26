package com.hfremovalsadelaide.invoices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hfremovalsadelaide.invoices.BuildConfig
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.auth.AuthRepository
import com.hfremovalsadelaide.invoices.jobs.CalendarView
import com.hfremovalsadelaide.invoices.jobs.JobsApi
import com.hfremovalsadelaide.invoices.jobs.JobsBackground
import com.hfremovalsadelaide.invoices.jobs.JobsViewModel
import com.hfremovalsadelaide.invoices.network.NetworkMonitor
import com.hfremovalsadelaide.invoices.offline.EncryptedStore
import com.hfremovalsadelaide.invoices.offline.OfflineSyncQueue
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import com.hfremovalsadelaide.invoices.settings.AppSettings
import com.hfremovalsadelaide.invoices.ui.auth.BrandedSignInScreen
import com.hfremovalsadelaide.invoices.ui.auth.SignInModalSheet
import com.hfremovalsadelaide.invoices.ui.components.DownloadProgressBanner
import com.hfremovalsadelaide.invoices.ui.components.SettingsSheet
import com.hfremovalsadelaide.invoices.ui.customers.CrmLaunchIntent
import com.hfremovalsadelaide.invoices.ui.customers.CustomersScreen
import com.hfremovalsadelaide.invoices.ui.dashboard.DashboardScreen
import com.hfremovalsadelaide.invoices.ui.invoices.CreateInvoiceScreen
import com.hfremovalsadelaide.invoices.ui.invoices.InvoiceListScreen
import com.hfremovalsadelaide.invoices.ui.invoices.InvoicesEvent
import com.hfremovalsadelaide.invoices.ui.invoices.InvoicesLoadState
import com.hfremovalsadelaide.invoices.ui.invoices.InvoicesViewModel
import com.hfremovalsadelaide.invoices.ui.jobs.JobsTab
import com.hfremovalsadelaide.invoices.ui.jobs.clearCameraCaptures
import com.hfremovalsadelaide.invoices.ui.more.MoreRouterScreen
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite

enum class AppDestination(
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD(R.string.tab_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_tab_dashboard"),
    JOBS(R.string.tab_jobs, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_tab_jobs"),
    INVOICES(R.string.tab_invoices, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "nav_tab_invoices"),
    CUSTOMERS(R.string.tab_customers, Icons.Filled.People, Icons.Outlined.People, "nav_tab_customers"),
    MORE(R.string.tab_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "nav_tab_more")
}

private const val NAV_LABEL_MAX_FONT_SCALE = 1.15f
private const val NAV_LABEL_START_SP = 11f
private const val NAV_LABEL_MIN_SP = 9f

/** Single-line navigation label. Font scale is capped so large accessibility sizes never wrap or clip the bar. */
@Composable
private fun NavLabel(text: String) {
    val density = LocalDensity.current
    // Shrink to fit (down to a readable floor) rather than ellipsising names like "Dashboard" and "Customers".
    var size by remember(text) { mutableStateOf(NAV_LABEL_START_SP.sp) }
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = minOf(density.fontScale, NAV_LABEL_MAX_FONT_SCALE))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = size,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            onTextLayout = { layout ->
                if (layout.hasVisualOverflow && size.value > NAV_LABEL_MIN_SP) size = (size.value * 0.92f).sp
            }
        )
    }
}

@Composable
fun MainAppShell(
    workspaceViewModel: WorkspaceViewModel = viewModel(),
    invoicesViewModel: InvoicesViewModel = viewModel(),
    initialJobId: String? = null,
    onJobIntentConsumed: () -> Unit = {}
) {
    val resources = LocalResources.current
    val appContext = LocalContext.current.applicationContext
    val appSettings = remember { AppSettings.get(appContext) }
    val networkMonitor = remember { NetworkMonitor(appContext) }
    val encryptedStore = remember { EncryptedStore(appContext) }
    val syncQueue = remember { OfflineSyncQueue.forApplication(appContext) }

    val jobsViewModel: JobsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                JobsViewModel(
                    gateway = JobsApi,
                    onSnapshot = { JobsBackground.onSnapshot(appContext, it) },
                    onPreferences = { JobsBackground.saveEnabled(appContext, it) },
                    syncQueue = syncQueue,
                    cacheRead = {
                        AuthRepository.readCachedUser(encryptedStore)?.id?.let {
                            encryptedStore.readText(AuthRepository.snapshotKey(it))
                        }
                    },
                    cacheWrite = { snapshot ->
                        AuthRepository.readCachedUser(encryptedStore)?.id?.let {
                            encryptedStore.writeText(AuthRepository.snapshotKey(it), snapshot)
                        }
                    },
                    isNetworkAvailable = { networkMonitor.refreshNow() },
                    scheduleOfflineSync = { OfflineSyncQueue.scheduleSync(appContext) }
                )
            }
        }
    )

    val authRepository = remember {
        AuthRepository(encryptedStore, syncQueue) {
            jobsViewModel.resetAccountState()
            invoicesViewModel.resetAccountState()
            workspaceViewModel.resetAccountState()
            clearCameraCaptures(appContext)
            OfflineSyncQueue.cancelSync(appContext)
            JobsBackground.clearAccountState(appContext)
            onJobIntentConsumed()
        }
    }
    val platformViewModel: PlatformViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlatformViewModel(
                    authRepository = authRepository,
                    syncQueue = syncQueue,
                    scheduleBackgroundSync = { OfflineSyncQueue.scheduleSync(appContext) }
                )
            }
        }
    )

    val platformState by platformViewModel.state.collectAsStateWithLifecycle()
    val jobsState by jobsViewModel.state.collectAsStateWithLifecycle()
    val workspaceState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val invoicesState by invoicesViewModel.uiState.collectAsStateWithLifecycle()
    val privacyModeEnabled by appSettings.privacyMode.collectAsStateWithLifecycle()
    val themeMode by appSettings.themeMode.collectAsStateWithLifecycle()
    val isConnected by networkMonitor.isConnected.collectAsStateWithLifecycle()

    DisposableEffect(networkMonitor) {
        networkMonitor.start()
        onDispose { networkMonitor.stop() }
    }

    LaunchedEffect(isConnected) {
        platformViewModel.setOfflineStatus(!isConnected)
    }

    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD) }
    var activeMoreSection by rememberSaveable { mutableStateOf(MoreSection.HUB) }
    var showCreateInvoice by rememberSaveable { mutableStateOf(false) }
    var crmLaunchIntent by remember { mutableStateOf<CrmLaunchIntent?>(null) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var bypassToWebAuth by rememberSaveable { mutableStateOf(false) }
    var webReloadSignal by remember { mutableIntStateOf(0) }
    val shellSnackbarHostState = remember { SnackbarHostState() }

    val visibleTabs = remember(platformState.user) { RoleUiPolicy.visibleDestinations(platformState.user) }
    LaunchedEffect(visibleTabs) {
        if (currentDestination !in visibleTabs) {
            currentDestination = visibleTabs.firstOrNull() ?: AppDestination.DASHBOARD
        }
    }

    // Load jobs & invoices when dashboard/jobs/invoices open
    LaunchedEffect(currentDestination, platformState.user, platformState.checkingSession) {
        if (platformState.checkingSession) return@LaunchedEffect
        val user = platformState.user ?: return@LaunchedEffect
        when (currentDestination) {
            AppDestination.DASHBOARD -> {
                jobsViewModel.load()
                if (user.can("reports.read")) platformViewModel.loadAnalytics()
                platformViewModel.loadNotifications()
            }
            AppDestination.JOBS -> {
                jobsViewModel.load()
                jobsViewModel.loadPreferences()
                if (user.can("staff.manage") || user.can("jobs.assign")) platformViewModel.loadStaff()
                if (user.can("fleet.read")) platformViewModel.loadFleet()
            }
            AppDestination.INVOICES -> {
                if (user.can("invoices.read")) invoicesViewModel.loadInvoices(isPullRefresh = false)
            }
            AppDestination.CUSTOMERS -> {
                if (user.can("crm.read")) platformViewModel.loadCrm()
            }
            AppDestination.MORE -> Unit
        }
    }

    val jobsReady = jobsState.snapshot != null && !jobsState.authRequired
    LaunchedEffect(jobsState.authRequired) {
        if (jobsState.authRequired) platformViewModel.refreshSession()
    }
    LaunchedEffect(initialJobId, platformState.user != null, jobsReady) {
        if (initialJobId == null || platformState.user == null) return@LaunchedEffect
        currentDestination = AppDestination.JOBS
        showCreateInvoice = false
        if (jobsReady) {
            jobsViewModel.openJob(initialJobId)
            onJobIntentConsumed()
        }
    }

    LaunchedEffect(jobsState.message) {
        jobsState.message?.let {
            jobsViewModel.messageShown()
            shellSnackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(platformState.feedbackMessage) {
        platformState.feedbackMessage?.let {
            platformViewModel.consumeFeedback()
            shellSnackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(invoicesState.actionMessage) {
        invoicesState.actionMessage?.let {
            invoicesViewModel.consumeActionMessage()
            shellSnackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(Unit) {
        invoicesViewModel.events.collect { event ->
            when (event) {
                is InvoicesEvent.InvoiceSaved -> {
                    webReloadSignal++
                    showCreateInvoice = false
                    currentDestination = AppDestination.INVOICES
                    val msgId = if (event.wasIssued) R.string.create_invoice_success_issued else R.string.create_invoice_success_draft
                    shellSnackbarHostState.showSnackbar(resources.getString(msgId), duration = SnackbarDuration.Short)
                }
                InvoicesEvent.RequireSignIn -> platformViewModel.openSignInSheet()
            }
        }
    }

    // Native gated login when completely signed out (unless opening Classic Web Workspace for admin web sign-in)
    if (platformState.checkingSession && platformState.user != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (platformState.authRequired && platformState.user == null && !bypassToWebAuth) {
        BrandedSignInScreen(
            busy = platformState.authBusy,
            error = platformState.authError,
            onLoginStaff = { email, pwd ->
                platformViewModel.loginStaff(email, pwd) {
                    jobsViewModel.load()
                    if (RoleUiPolicy.canWriteInvoices(platformViewModel.state.value.user)) invoicesViewModel.loadInvoices()
                }
            },
            onLoginAdmin = { pwd ->
                platformViewModel.loginAdmin(pwd) {
                    jobsViewModel.load()
                    invoicesViewModel.loadInvoices()
                }
            },
            onAcceptInvite = { tok, pwd ->
                platformViewModel.acceptInvite(tok, pwd) { jobsViewModel.load() }
            },
            onResetPassword = { tok, pwd -> platformViewModel.resetPassword(tok, pwd) },
            onUseLegacyWebLogin = {
                bypassToWebAuth = true
                currentDestination = AppDestination.MORE
                activeMoreSection = MoreSection.WORKSPACE
            }
        )
        return
    }

    key(platformState.user?.id, platformState.user?.role, platformState.user?.permissions) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            AdaptiveShellFrame(
                useRail = useNavigationRail,
                rail = {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding().testTag("adaptive_navigation_rail")
                    ) {
                        visibleTabs.forEach { destination ->
                            val selected = currentDestination == destination && !showCreateInvoice
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    showCreateInvoice = false
                                    if (currentDestination == AppDestination.MORE && activeMoreSection == MoreSection.WORKSPACE && destination != AppDestination.MORE) {
                                        platformViewModel.refreshSession()
                                    }
                                    currentDestination = destination
                                },
                                icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = null) },
                                label = { NavLabel(stringResource(id = destination.labelResId)) },
                                alwaysShowLabel = true,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(destination.testTag)
                            )
                        }
                    }
                },
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            windowInsets = WindowInsets.navigationBars,
                            modifier = Modifier.testTag("adaptive_navigation_bar")
                        ) {
                            visibleTabs.forEach { destination ->
                                val selected = currentDestination == destination && !showCreateInvoice
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        showCreateInvoice = false
                                        if (currentDestination == AppDestination.MORE && activeMoreSection == MoreSection.WORKSPACE && destination != AppDestination.MORE) {
                                            platformViewModel.refreshSession()
                                        }
                                        currentDestination = destination
                                    },
                                    icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = null) },
                                    label = { NavLabel(stringResource(id = destination.labelResId)) },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(destination.testTag)
                                )
                            }
                        }
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = shellSnackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = ForestCanopy,
                            contentColor = PureWhite,
                            actionColor = GoldAccent
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (showCreateInvoice) {
                        CreateInvoiceScreen(
                            formState = invoicesState.form,
                            availableCustomers = invoicesState.availableCustomers,
                            onSelectCustomer = invoicesViewModel::onSelectExistingCustomer,
                            onCustomerNameChanged = invoicesViewModel::onCustomerNameChanged,
                            onCustomerAddressChanged = invoicesViewModel::onCustomerAddressChanged,
                            onServicesPerformedChanged = invoicesViewModel::onServicesPerformedChanged,
                            onTotalAmountChanged = invoicesViewModel::onTotalAmountChanged,
                            onIssueDateChanged = invoicesViewModel::onIssueDateChanged,
                            onDueDateChanged = invoicesViewModel::onDueDateChanged,
                            onIssueImmediatelyChanged = invoicesViewModel::onIssueImmediatelyChanged,
                            onResetForm = invoicesViewModel::resetForm,
                            onSubmit = invoicesViewModel::submitNewInvoice,
                            onNavigateBack = { showCreateInvoice = false },
                            onGoToSignIn = { platformViewModel.openSignInSheet() },
                            availableRateCards = invoicesState.rateCards,
                            onSegmentChanged = invoicesViewModel::onSegmentChanged,
                            onRateCardSelected = invoicesViewModel::onRateCardSelected,
                            onPickupChanged = invoicesViewModel::onPickupChanged,
                            onDeliveryChanged = invoicesViewModel::onDeliveryChanged,
                            onAddExtraItem = invoicesViewModel::addExtraItem,
                            onRemoveExtraItem = invoicesViewModel::removeExtraItem,
                            modifier = Modifier.fillMaxSize().zIndex(4f)
                        )
                    } else {
                        when (currentDestination) {
                            AppDestination.DASHBOARD -> DashboardScreen(
                                platformState = platformState,
                                jobsSnapshot = jobsState.snapshot,
                                jobsLoading = jobsState.loading,
                                onRefreshAll = {
                                    jobsViewModel.load()
                                    platformViewModel.preloadRoleSafeModules()
                                },
                                onOpenSignIn = { platformViewModel.openSignInSheet() },
                                onOpenNotifications = {
                                    currentDestination = AppDestination.MORE
                                    activeMoreSection = MoreSection.NOTIFICATIONS
                                },
                                onOpenSettings = { showSettingsSheet = true },
                                onSyncNow = {
                                    platformViewModel.flushOfflineQueue { jobsViewModel.load() }
                                },
                                onOpenOfflineQueue = {
                                    currentDestination = AppDestination.MORE
                                    activeMoreSection = MoreSection.OFFLINE_QUEUE
                                },
                                onOpenJob = { jobId ->
                                    currentDestination = AppDestination.JOBS
                                    jobsViewModel.openJob(jobId)
                                },
                                onNavigateJobsDispatch = {
                                    currentDestination = AppDestination.JOBS
                                    if (RoleUiPolicy.canDispatch(platformState.user)) {
                                        jobsViewModel.selectView(CalendarView.DISPATCH)
                                    }
                                },
                                onNavigateNewInvoice = {
                                    if (invoicesState.loadState !is InvoicesLoadState.Loaded) {
                                        invoicesViewModel.loadInvoices(isPullRefresh = false)
                                    }
                                    showCreateInvoice = true
                                },
                                onNavigateAnalytics = {
                                    currentDestination = AppDestination.MORE
                                    activeMoreSection = MoreSection.ANALYTICS
                                },
                                onNavigateTimesheets = {
                                    currentDestination = AppDestination.MORE
                                    activeMoreSection = MoreSection.TIMESHEETS
                                },
                                onViewCalendar = {
                                    currentDestination = AppDestination.JOBS
                                    jobsViewModel.selectView(CalendarView.AGENDA)
                                },
                                onAddCustomer = {
                                    crmLaunchIntent = CrmLaunchIntent.ADD_CUSTOMER
                                    currentDestination = AppDestination.CUSTOMERS
                                },
                                onCreateQuotation = {
                                    crmLaunchIntent = CrmLaunchIntent.CREATE_QUOTE
                                    currentDestination = AppDestination.CUSTOMERS
                                },
                                onSyncBookings = { jobsViewModel.syncCalendar() },
                                modifier = Modifier.fillMaxSize()
                            )

                            AppDestination.JOBS -> JobsTab(
                                state = jobsState,
                                viewModel = jobsViewModel,
                                onGoToSignIn = { platformViewModel.openSignInSheet() },
                                onDownloadCompletionPdf = { jobId, includePhotos ->
                                    val photoQuery = if (includePhotos) "?photos=1" else ""
                                    workspaceViewModel.downloadAuthenticatedDocument(
                                        url = "${TrustedOriginPolicy.PRODUCTION_URL}/api/jobs/$jobId/completion-pdf$photoQuery",
                                        expectedMimeType = "application/pdf",
                                        openPrintImmediately = false
                                    )
                                },
                                onDownloadInventoryPdf = { jobId ->
                                    workspaceViewModel.downloadAuthenticatedDocument(
                                        url = "${TrustedOriginPolicy.PRODUCTION_URL}/api/jobs/$jobId/inventory?format=pdf",
                                        expectedMimeType = "application/pdf",
                                        openPrintImmediately = false
                                    )
                                },
                                permissions = platformState.user?.permissions.orEmpty(),
                                canDispatch = RoleUiPolicy.canDispatch(platformState.user),
                                availableStaff = platformState.staff,
                                availableVehicles = platformState.fleet?.vehicles.orEmpty(),
                                modifier = Modifier.fillMaxSize()
                            )

                            AppDestination.INVOICES -> InvoiceListScreen(
                                uiState = invoicesState,
                                savedDocumentsCount = workspaceState.savedDocuments.size,
                                onRefresh = { isPull -> invoicesViewModel.loadInvoices(isPullRefresh = isPull) },
                                onSearchQueryChanged = invoicesViewModel::onSearchQueryChanged,
                                onStatusFilterSelected = invoicesViewModel::onStatusFilterSelected,
                                onCreateInvoiceClick = { showCreateInvoice = true },
                                onGoToSignInClick = { platformViewModel.openSignInSheet() },
                                onDownloadPdfClick = { invoice ->
                                    workspaceViewModel.downloadAuthenticatedDocument(
                                        url = "${TrustedOriginPolicy.PRODUCTION_URL}/api/pdf/${invoice.id}",
                                        expectedMimeType = "application/pdf",
                                        openPrintImmediately = false
                                    )
                                },
                                onPrintPdfClick = { invoice ->
                                    workspaceViewModel.downloadAuthenticatedDocument(
                                        url = "${TrustedOriginPolicy.PRODUCTION_URL}/api/pdf/${invoice.id}?inline",
                                        expectedMimeType = "application/pdf",
                                        openPrintImmediately = true
                                    )
                                },
                                onSavedDocumentsClick = { workspaceViewModel.openSavedDocumentsSheet() },
                                onNavigateBackToWorkspace = { currentDestination = AppDestination.DASHBOARD },
                                onSelectInvoice = invoicesViewModel::selectInvoice,
                                onRecordPayment = invoicesViewModel::recordPayment,
                                onMarkPaid = invoicesViewModel::markPaid,
                                onVoidInvoice = invoicesViewModel::voidInvoice,
                                onDuplicateInvoice = invoicesViewModel::duplicateInvoice,
                                canWriteInvoices = RoleUiPolicy.canWriteInvoices(platformState.user),
                                modifier = Modifier.fillMaxSize()
                            )

                            AppDestination.CUSTOMERS -> CustomersScreen(
                                platformState = platformState,
                                platformViewModel = platformViewModel,
                                allJobs = jobsState.snapshot?.jobs.orEmpty(),
                                allInvoices = invoicesState.allInvoices,
                                modifier = Modifier.fillMaxSize(),
                                launchIntent = crmLaunchIntent,
                                onLaunchIntentConsumed = { crmLaunchIntent = null },
                                jobsLoaded = jobsState.snapshot != null,
                                invoicesLoaded = invoicesState.loadState is InvoicesLoadState.Loaded
                            )

                            AppDestination.MORE -> MoreRouterScreen(
                                section = activeMoreSection,
                                platformState = platformState,
                                platformViewModel = platformViewModel,
                                onSelectSection = {
                                    if (activeMoreSection == MoreSection.WORKSPACE && it != MoreSection.WORKSPACE) {
                                        platformViewModel.refreshSession()
                                    }
                                    activeMoreSection = it
                                },
                                onOpenSettings = { showSettingsSheet = true },
                                onDownloadCsvExport = {
                                    workspaceViewModel.downloadAuthenticatedDocument(
                                        url = "${TrustedOriginPolicy.PRODUCTION_URL}/api/export?format=csv",
                                        expectedMimeType = "text/csv",
                                        openPrintImmediately = false
                                    )
                                },
                                onDownloadStatementPdf = { statementId ->
                                    workspaceViewModel.downloadAuthenticatedDocument(
                                        url = "${TrustedOriginPolicy.PRODUCTION_URL}/api/timesheets/statements/$statementId/pdf",
                                        expectedMimeType = "application/pdf",
                                        openPrintImmediately = false
                                    )
                                },
                                onOpenJob = { jobId ->
                                    currentDestination = AppDestination.JOBS
                                    jobsViewModel.openJob(jobId)
                                },
                                workspaceContent = {
                                    WorkspaceScreen(
                                        viewModel = workspaceViewModel,
                                        isActiveTab = true,
                                        externalReloadSignal = webReloadSignal,
                                        applyNavigationBarPadding = useNavigationRail
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    DownloadProgressBanner(
                        visible = workspaceState.isDownloading,
                        statusText = stringResource(id = workspaceState.downloadStatusResId),
                        progress = workspaceState.downloadProgress?.fraction,
                        onCancel = { workspaceViewModel.cancelDownload() },
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(5f)
                    )
                }
            }
        }

    }

    if (showSettingsSheet) {
        SettingsSheet(
            privacyModeEnabled = privacyModeEnabled,
            onPrivacyModeChange = { appSettings.setPrivacyMode(it) },
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = { showSettingsSheet = false },
            themeMode = themeMode,
            onThemeModeChange = { appSettings.setThemeMode(it) },
            currentUser = platformState.user,
            onSignInClick = { platformViewModel.openSignInSheet() },
            onSignOutClick = {
                bypassToWebAuth = false
                showSettingsSheet = false
                platformViewModel.logout()
            }
        )
    }

    if (platformState.showSignInSheet) {
        SignInModalSheet(
            busy = platformState.authBusy,
            error = platformState.authError,
            onLoginStaff = { email, pwd ->
                platformViewModel.loginStaff(email, pwd) {
                    jobsViewModel.load()
                    if (RoleUiPolicy.canWriteInvoices(platformViewModel.state.value.user)) invoicesViewModel.loadInvoices()
                }
            },
            onLoginAdmin = { pwd ->
                platformViewModel.loginAdmin(pwd) {
                    jobsViewModel.load()
                    invoicesViewModel.loadInvoices()
                }
            },
            onAcceptInvite = { tok, pwd -> platformViewModel.acceptInvite(tok, pwd) { jobsViewModel.load() } },
            onResetPassword = { tok, pwd -> platformViewModel.resetPassword(tok, pwd) },
            onUseLegacyWebLogin = {
                platformViewModel.dismissSignInSheet()
                bypassToWebAuth = true
                currentDestination = AppDestination.MORE
                activeMoreSection = MoreSection.WORKSPACE
            },
            onDismiss = { platformViewModel.dismissSignInSheet() }
        )
    }
}
