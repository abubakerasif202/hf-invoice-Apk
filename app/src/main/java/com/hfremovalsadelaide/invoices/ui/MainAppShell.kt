package com.hfremovalsadelaide.invoices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import com.hfremovalsadelaide.invoices.ui.components.DownloadProgressBanner
import com.hfremovalsadelaide.invoices.ui.invoices.CreateInvoiceScreen
import com.hfremovalsadelaide.invoices.ui.invoices.InvoiceListScreen
import com.hfremovalsadelaide.invoices.ui.invoices.InvoicesEvent
import com.hfremovalsadelaide.invoices.ui.invoices.InvoicesLoadState
import com.hfremovalsadelaide.invoices.ui.invoices.InvoicesViewModel
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.ForestSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.MutedSage
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.ui.theme.SageOutline

enum class AppDestination(
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    WORKSPACE(
        labelResId = R.string.tab_workspace,
        selectedIcon = Icons.Filled.Language,
        unselectedIcon = Icons.Outlined.Language,
        testTag = "nav_tab_workspace"
    ),
    INVOICES(
        labelResId = R.string.tab_invoices,
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        testTag = "nav_tab_invoices"
    ),
    NEW_INVOICE(
        labelResId = R.string.tab_new_invoice,
        selectedIcon = Icons.Filled.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline,
        testTag = "nav_tab_new_invoice"
    )
}

/**
 * Adaptive top-level shell hosting the live Workspace WebView alongside the native Jetpack Compose
 * Invoices List and Create Invoice Form screens. Uses NavigationBar on phones and NavigationRail
 * on tablets while keeping the WebView alive across tab switches.
 */
@Composable
fun MainAppShell(
    workspaceViewModel: WorkspaceViewModel = viewModel(),
    invoicesViewModel: InvoicesViewModel = viewModel()
) {
    val resources = LocalResources.current
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.WORKSPACE) }
    var webReloadSignal by remember { mutableIntStateOf(0) }
    val shellSnackbarHostState = remember { SnackbarHostState() }

    val workspaceState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val invoicesState by invoicesViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(currentDestination) {
        if (currentDestination == AppDestination.INVOICES ||
            (currentDestination == AppDestination.NEW_INVOICE &&
                invoicesState.loadState !is InvoicesLoadState.Loaded)
        ) {
            invoicesViewModel.loadInvoices(isPullRefresh = false)
        }
    }

    LaunchedEffect(Unit) {
        invoicesViewModel.events.collect { event ->
            when (event) {
                is InvoicesEvent.InvoiceSaved -> {
                    webReloadSignal++
                    currentDestination = AppDestination.INVOICES
                    val msgId = if (event.wasIssued) {
                        R.string.create_invoice_success_issued
                    } else {
                        R.string.create_invoice_success_draft
                    }
                    shellSnackbarHostState.showSnackbar(
                        message = resources.getString(msgId),
                        duration = SnackbarDuration.Short
                    )
                }

                InvoicesEvent.RequireSignIn -> {
                    shellSnackbarHostState.showSnackbar(
                        message = resources.getString(R.string.create_invoice_auth_needed),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = PureWhite,
                    contentColor = ForestGreen,
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .testTag("adaptive_navigation_rail")
                ) {
                    AppDestination.entries.forEach { destination ->
                        val selected = currentDestination == destination
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = stringResource(id = destination.labelResId)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(id = destination.labelResId),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ForestGreen,
                                selectedTextColor = ForestGreen,
                                indicatorColor = ForestSoftTint,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            ),
                            modifier = Modifier.testTag(destination.testTag)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(SageOutline)
                        .padding(end = 1.dp)
                )

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(hostState = shellSnackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = ForestCanopy,
                                contentColor = PureWhite,
                                actionColor = GoldAccent
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) { innerPadding ->
                    DestinationContentHost(
                        currentDestination = currentDestination,
                        workspaceViewModel = workspaceViewModel,
                        invoicesViewModel = invoicesViewModel,
                        workspaceState = workspaceState,
                        invoicesState = invoicesState,
                        webReloadSignal = webReloadSignal,
                        applyBottomNavInsetInWorkspace = true,
                        onNavigateTo = { currentDestination = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(hostState = shellSnackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = ForestCanopy,
                            contentColor = PureWhite,
                            actionColor = GoldAccent
                        )
                    }
                },
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(SageOutline)
                        )
                        NavigationBar(
                            containerColor = PureWhite,
                            contentColor = ForestGreen,
                            windowInsets = WindowInsets.navigationBars,
                            modifier = Modifier.testTag("adaptive_navigation_bar")
                        ) {
                            AppDestination.entries.forEach { destination ->
                                val selected = currentDestination == destination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) {
                                                destination.selectedIcon
                                            } else {
                                                destination.unselectedIcon
                                            },
                                            contentDescription = stringResource(id = destination.labelResId)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(id = destination.labelResId),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ForestGreen,
                                        selectedTextColor = ForestGreen,
                                        indicatorColor = ForestSoftTint,
                                        unselectedIconColor = MutedSage,
                                        unselectedTextColor = MutedSage
                                    ),
                                    modifier = Modifier.testTag(destination.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                DestinationContentHost(
                    currentDestination = currentDestination,
                    workspaceViewModel = workspaceViewModel,
                    invoicesViewModel = invoicesViewModel,
                    workspaceState = workspaceState,
                    invoicesState = invoicesState,
                    webReloadSignal = webReloadSignal,
                    applyBottomNavInsetInWorkspace = false,
                    onNavigateTo = { currentDestination = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun DestinationContentHost(
    currentDestination: AppDestination,
    workspaceViewModel: WorkspaceViewModel,
    invoicesViewModel: InvoicesViewModel,
    workspaceState: WorkspaceUiState,
    invoicesState: com.hfremovalsadelaide.invoices.ui.invoices.InvoicesUiState,
    webReloadSignal: Int,
    applyBottomNavInsetInWorkspace: Boolean,
    onNavigateTo: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Keep WorkspaceScreen alive in composition so the WebView session and DOM state persist across tab switches
        val isWorkspaceActive = currentDestination == AppDestination.WORKSPACE
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (isWorkspaceActive) 1f else 0f)
                .alpha(if (isWorkspaceActive) 1f else 0f)
        ) {
            WorkspaceScreen(
                viewModel = workspaceViewModel,
                isActiveTab = isWorkspaceActive,
                externalReloadSignal = webReloadSignal,
                applyNavigationBarPadding = applyBottomNavInsetInWorkspace
            )
        }

        if (currentDestination == AppDestination.INVOICES) {
            InvoiceListScreen(
                uiState = invoicesState,
                savedDocumentsCount = workspaceState.savedDocuments.size,
                onRefresh = { isPull -> invoicesViewModel.loadInvoices(isPullRefresh = isPull) },
                onSearchQueryChanged = invoicesViewModel::onSearchQueryChanged,
                onStatusFilterSelected = invoicesViewModel::onStatusFilterSelected,
                onCreateInvoiceClick = { onNavigateTo(AppDestination.NEW_INVOICE) },
                onGoToSignInClick = { onNavigateTo(AppDestination.WORKSPACE) },
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
                onNavigateBackToWorkspace = { onNavigateTo(AppDestination.WORKSPACE) },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            )
        }

        if (currentDestination == AppDestination.NEW_INVOICE) {
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
                onNavigateBack = { onNavigateTo(AppDestination.INVOICES) },
                onGoToSignIn = { onNavigateTo(AppDestination.WORKSPACE) },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            )
        }

        if (!isWorkspaceActive) {
            DownloadProgressBanner(
                visible = workspaceState.isDownloading,
                statusText = stringResource(id = workspaceState.downloadStatusResId),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
            )
        }
    }
}
