package com.hfremovalsadelaide.invoices.ui

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.saveable.rememberSaveable
import com.hfremovalsadelaide.invoices.BuildConfig
import com.hfremovalsadelaide.invoices.settings.AppSettings
import com.hfremovalsadelaide.invoices.ui.components.SettingsSheet
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.pdf.DownloadNotificationHelper
import com.hfremovalsadelaide.invoices.pdf.DownloadedDocument
import com.hfremovalsadelaide.invoices.pdf.PdfActions
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import com.hfremovalsadelaide.invoices.ui.components.BrandedSplashOverlay
import com.hfremovalsadelaide.invoices.ui.components.ConnectionErrorScreen
import com.hfremovalsadelaide.invoices.ui.components.DocumentReadySheet
import com.hfremovalsadelaide.invoices.ui.components.DownloadProgressBanner
import com.hfremovalsadelaide.invoices.ui.components.JsDialogHost
import com.hfremovalsadelaide.invoices.ui.components.OfflineStatusBanner
import com.hfremovalsadelaide.invoices.ui.components.SavedDocumentsSheet
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.webview.InvoiceWebViewCallbacks
import com.hfremovalsadelaide.invoices.webview.InvoiceWebViewFactory
import kotlinx.coroutines.launch

@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel = viewModel(),
    isActiveTab: Boolean = true,
    externalReloadSignal: Int = 0,
    applyNavigationBarPadding: Boolean = true
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appSettings = remember { AppSettings.get(context) }
    val privacyModeEnabled by appSettings.privacyMode.collectAsStateWithLifecycle()
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    // Survives Activity recreation and process death so the user returns to the page they were on.
    val lastPageUrl = rememberSaveable { mutableStateOf<String?>(null) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // Zero-permission Android Photo Picker for logo uploads (PNG/JPEG)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    }

    // Zero-permission SAF Document Picker fallback for non-image file inputs
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    }

    // Android 13+ notification permission request (non-blocking if declined)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Downloads always succeed regardless of notification preference */ }

    val showSnackbar: (Int) -> Unit = { resId ->
        scope.launch {
            snackbarHostState.showSnackbar(
                message = resources.getString(resId),
                duration = SnackbarDuration.Short
            )
        }
    }

    val handleOpenDocument: (DownloadedDocument) -> Unit = { doc ->
        val opened = PdfActions.openDocument(context, doc)
        if (!opened) {
            showSnackbar(R.string.error_no_pdf_viewer)
        }
    }

    val handleShareDocument: (DownloadedDocument) -> Unit = { doc ->
        PdfActions.shareDocument(context, doc)
    }

    val handlePrintDocument: (DownloadedDocument) -> Unit = { doc ->
        val hostActivity = activity
        if (hostActivity == null || !PdfActions.printPdf(hostActivity, doc)) {
            showSnackbar(R.string.download_failed_generic)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkspaceEvent.ShowMessage -> showSnackbar(event.messageResId)
                is WorkspaceEvent.PrintDocument -> handlePrintDocument(event.document)
                is WorkspaceEvent.OpenExternalUri -> {
                    val opened = PdfActions.openExternalUri(context, event.uri)
                    showSnackbar(
                        if (opened) R.string.external_link_opened else R.string.error_no_browser
                    )
                }
                WorkspaceEvent.RequestNotificationPermissionIfNeeded -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !DownloadNotificationHelper.hasNotificationPermission(context)
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                WorkspaceEvent.SessionExpired -> {
                    // Take the user straight to the login page, and say why.
                    webViewRef?.loadUrl(TrustedOriginPolicy.PRODUCTION_URL)
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.download_failed_auth),
                        actionLabel = resources.getString(R.string.session_expired_action),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        webViewRef?.loadUrl(TrustedOriginPolicy.PRODUCTION_URL)
                    }
                }
            }
        }
    }

    LaunchedEffect(externalReloadSignal) {
        if (externalReloadSignal > 0) {
            webViewRef?.reload()
        }
    }

    // Android Back button navigation: close sheets/dialogs first, then navigate WebView history
    BackHandler(
        enabled = uiState.activeDocument != null ||
            uiState.showSavedDocumentsSheet ||
            uiState.pendingJsDialog != null ||
            (isActiveTab && uiState.canGoBack)
    ) {
        when {
            uiState.pendingJsDialog != null -> viewModel.dismissJsDialog(confirmed = false)
            uiState.activeDocument != null -> viewModel.dismissActiveDocument()
            uiState.showSavedDocumentsSheet -> viewModel.dismissSavedDocumentsSheet()
            isActiveTab && webViewRef?.canGoBack() == true -> webViewRef?.goBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            CookieManager.getInstance().flush()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    val webCallbacks = remember(viewModel) {
        object : InvoiceWebViewCallbacks {
            override fun onPageStarted(canGoBack: Boolean) {
                viewModel.onPageStarted(canGoBack)
            }

            override fun onProgressChanged(progress: Int) {
                viewModel.onProgressChanged(progress)
            }

            override fun onPageFinished(canGoBack: Boolean) {
                viewModel.onPageFinished(canGoBack)
            }

            override fun onPageUrl(url: String?) {
                if (TrustedOriginPolicy.restorableUrl(url) == url) lastPageUrl.value = url
            }

            override fun onScrollAtTopChanged(isAtTop: Boolean) {
                viewModel.onWebViewScrollPositionChanged(isAtTop)
            }

            override fun onMainFrameError(isOfflineHint: Boolean) {
                viewModel.onMainFrameError(isOfflineHint)
            }

            override fun onSslErrorBlocked() {
                viewModel.onSslErrorBlocked()
            }

            override fun onDownloadPdf(url: String, openPrintImmediately: Boolean) {
                viewModel.downloadAuthenticatedDocument(
                    url = url,
                    expectedMimeType = "application/pdf",
                    openPrintImmediately = openPrintImmediately
                )
            }

            override fun onDownloadCsv(url: String) {
                viewModel.downloadAuthenticatedDocument(
                    url = url,
                    expectedMimeType = "text/csv",
                    openPrintImmediately = false
                )
            }

            override fun onFallbackDownload(
                url: String,
                mimeType: String,
                contentDisposition: String?
            ) {
                viewModel.downloadAuthenticatedDocument(
                    url = url,
                    expectedMimeType = mimeType,
                    contentDispositionHint = contentDisposition,
                    openPrintImmediately = false
                )
            }

            override fun onOpenExternalUri(uri: Uri) {
                viewModel.onExternalNavigationRequested(uri)
            }

            override fun onUntrustedNavigationBlocked() {
                viewModel.onUntrustedNavigationBlocked()
            }

            override fun onShowFileChooser(
                filePathCallback: ValueCallback<Array<Uri>>,
                acceptTypes: Array<String>
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback

                val normalized = acceptTypes
                    .flatMap { it.split(',') }
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }

                val wantsOnlyImages = normalized.isNotEmpty() &&
                    normalized.all { it.startsWith("image/") }

                return try {
                    if (wantsOnlyImages) {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        val mimeTypes = normalized.ifEmpty { listOf("*/*") }.toTypedArray()
                        documentPickerLauncher.launch(mimeTypes)
                    }
                    true
                } catch (_: Exception) {
                    pendingFileCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }

            override fun onJsAlert(message: String, result: JsResult): Boolean {
                viewModel.showJsDialog(JsDialogRequest.Alert(message, result))
                return true
            }

            override fun onJsConfirm(message: String, result: JsResult): Boolean {
                viewModel.showJsDialog(JsDialogRequest.Confirm(message, result))
                return true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                WorkspaceTopBar(
                    canGoBack = uiState.canGoBack,
                    isLoading = uiState.isLoading,
                    loadProgress = uiState.loadProgress,
                    savedDocumentsCount = uiState.savedDocuments.size,
                    onBackClick = { webViewRef?.goBack() },
                    onSavedDocumentsClick = { viewModel.openSavedDocumentsSheet() },
                    onSettingsClick = { showSettingsSheet = true },
                    onRefreshClick = {
                        viewModel.onStartRefresh()
                        val view = webViewRef
                        if (view != null) {
                            if (view.url.isNullOrBlank()) {
                                view.loadUrl(TrustedOriginPolicy.PRODUCTION_URL)
                            } else {
                                view.reload()
                            }
                        }
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = if (applyNavigationBarPadding) Modifier.navigationBarsPadding() else Modifier
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = ForestCanopy,
                        contentColor = PureWhite,
                        actionColor = GoldAccent
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .then(if (applyNavigationBarPadding) Modifier.navigationBarsPadding() else Modifier)
                    .imePadding()
            ) {
                OfflineStatusBanner(
                    visible = !uiState.isOnline && uiState.errorKind == ConnectionErrorKind.NONE
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("invoice_webview_container"),
                        factory = { ctx ->
                            val webView = InvoiceWebViewFactory.create(ctx, webCallbacks, lastPageUrl.value)
                            webViewRef = webView

                            SwipeRefreshLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setColorSchemeColors(
                                    0xFF073D2F.toInt(),
                                    0xFFC69B49.toInt()
                                )
                                setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
                                setOnRefreshListener {
                                    viewModel.onStartRefresh()
                                    if (webView.url.isNullOrBlank()) {
                                        webView.loadUrl(TrustedOriginPolicy.PRODUCTION_URL)
                                    } else {
                                        webView.reload()
                                    }
                                }
                                addView(webView)
                            }
                        },
                        update = { swipeRefreshLayout ->
                            if (swipeRefreshLayout.isRefreshing != uiState.isRefreshing) {
                                swipeRefreshLayout.isRefreshing = uiState.isRefreshing
                            }
                            swipeRefreshLayout.isEnabled =
                                uiState.errorKind == ConnectionErrorKind.NONE && !uiState.showSplash
                        }
                    )

                    if (uiState.errorKind != ConnectionErrorKind.NONE) {
                        ConnectionErrorScreen(
                            errorKind = uiState.errorKind,
                            onRetry = {
                                if (viewModel.onRetryConnection()) {
                                    val view = webViewRef
                                    if (view != null) {
                                        val targetUrl = view.url
                                            ?.takeIf { TrustedOriginPolicy.isTrustedOrigin(it) }
                                            ?: TrustedOriginPolicy.PRODUCTION_URL
                                        view.loadUrl(targetUrl)
                                    }
                                }
                            }
                        )
                    }

                    DownloadProgressBanner(
                        visible = uiState.isDownloading,
                        statusText = stringResource(id = uiState.downloadStatusResId),
                        progress = uiState.downloadProgress?.fraction,
                        onCancel = { viewModel.cancelDownload() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        BrandedSplashOverlay(
            visible = uiState.showSplash,
            modifier = Modifier.fillMaxSize()
        )
    }

    val readyDoc = uiState.activeDocument
    if (readyDoc != null) {
        DocumentReadySheet(
            document = readyDoc,
            onOpen = {
                viewModel.dismissActiveDocument()
                handleOpenDocument(it)
            },
            onShare = {
                viewModel.dismissActiveDocument()
                handleShareDocument(it)
            },
            onPrint = {
                viewModel.dismissActiveDocument()
                handlePrintDocument(it)
            },
            onDismiss = { viewModel.dismissActiveDocument() }
        )
    }

    if (uiState.showSavedDocumentsSheet) {
        SavedDocumentsSheet(
            documents = uiState.savedDocuments,
            onOpen = {
                viewModel.dismissSavedDocumentsSheet()
                handleOpenDocument(it)
            },
            onShare = {
                viewModel.dismissSavedDocumentsSheet()
                handleShareDocument(it)
            },
            onPrint = {
                viewModel.dismissSavedDocumentsSheet()
                handlePrintDocument(it)
            },
            onDismiss = { viewModel.dismissSavedDocumentsSheet() }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            privacyModeEnabled = privacyModeEnabled,
            onPrivacyModeChange = { appSettings.setPrivacyMode(it) },
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = { showSettingsSheet = false }
        )
    }

    JsDialogHost(
        request = uiState.pendingJsDialog,
        onDismiss = { confirmed -> viewModel.dismissJsDialog(confirmed) }
    )
}

@Composable
private fun WorkspaceTopBar(
    canGoBack: Boolean,
    isLoading: Boolean,
    loadProgress: Int,
    savedDocumentsCount: Int,
    onBackClick: () -> Unit,
    onSavedDocumentsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Surface(
        color = ForestCanopy,
        contentColor = PureWhite,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workspace_top_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(58.dp)
                    .padding(horizontal = 12.dp)
            ) {
                val isWideScreen = maxWidth >= 600.dp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isWideScreen) 12.dp else 0.dp)
                ) {
                    if (canGoBack) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("nav_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(id = R.string.nav_back_description),
                                tint = PureWhite
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PureWhite,
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.8f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.hf_logo),
                            contentDescription = stringResource(id = R.string.splash_logo_description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(
                                id = if (isWideScreen) R.string.app_name else R.string.app_name_short
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isWideScreen) {
                            Text(
                                text = stringResource(id = R.string.brand_workspace_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onSavedDocumentsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("saved_pdfs_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (savedDocumentsCount > 0) {
                                    Badge(
                                        containerColor = GoldAccent,
                                        contentColor = ForestCanopy
                                    ) {
                                        Text(
                                            text = if (savedDocumentsCount > 9) "9+" else savedDocumentsCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = stringResource(id = R.string.nav_downloads_description),
                                tint = PureWhite
                            )
                        }
                    }

                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(id = R.string.nav_settings_description),
                            tint = PureWhite
                        )
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.nav_refresh_description),
                            tint = PureWhite
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(GoldAccent.copy(alpha = 0.55f))
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoading && loadProgress in 1..99,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        progress = { (loadProgress.coerceIn(5, 100)) / 100f },
                        color = GoldAccent,
                        trackColor = ForestGreen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .testTag("page_loading_progress")
                    )
                }
            }
        }
    }
}
