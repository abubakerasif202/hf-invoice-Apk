package com.hfremovalsadelaide.invoices.ui

import android.app.Application
import android.net.Uri
import android.webkit.JsResult
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.network.NetworkMonitor
import com.hfremovalsadelaide.invoices.pdf.AuthenticatedDocumentDownloader
import com.hfremovalsadelaide.invoices.pdf.DownloadNotificationHelper
import com.hfremovalsadelaide.invoices.pdf.DocumentDownloader
import com.hfremovalsadelaide.invoices.pdf.DownloadOutcome
import com.hfremovalsadelaide.invoices.pdf.DownloadProgress
import com.hfremovalsadelaide.invoices.pdf.DownloadedDocument
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionErrorKind {
    NONE,
    OFFLINE,
    CONNECTION_FAILED,
    SSL_BLOCKED
}

sealed interface JsDialogRequest {
    val message: String
    val jsResult: JsResult

    data class Alert(
        override val message: String,
        override val jsResult: JsResult
    ) : JsDialogRequest

    data class Confirm(
        override val message: String,
        override val jsResult: JsResult
    ) : JsDialogRequest
}

data class WorkspaceUiState(
    val showSplash: Boolean = true,
    val isLoading: Boolean = true,
    val loadProgress: Int = 10,
    val isRefreshing: Boolean = false,
    val canGoBack: Boolean = false,
    val isWebViewAtTop: Boolean = true,
    val isOnline: Boolean = true,
    val errorKind: ConnectionErrorKind = ConnectionErrorKind.NONE,
    val isDownloading: Boolean = false,
    @StringRes val downloadStatusResId: Int = R.string.download_in_progress,
    /** Null while no download runs, or while the server has not announced a length yet. */
    val downloadProgress: DownloadProgress? = null,
    val activeDocument: DownloadedDocument? = null,
    val showSavedDocumentsSheet: Boolean = false,
    val savedDocuments: List<DownloadedDocument> = emptyList(),
    val pendingJsDialog: JsDialogRequest? = null
)

sealed interface WorkspaceEvent {
    data class ShowMessage(@StringRes val messageResId: Int) : WorkspaceEvent
    data class PrintDocument(val document: DownloadedDocument) : WorkspaceEvent
    data class OpenExternalUri(val uri: Uri) : WorkspaceEvent
    data object RequestNotificationPermissionIfNeeded : WorkspaceEvent
    /** The server session ended; the UI shows "Session Expired — Sign In Again" and the login page. */
    data object SessionExpired : WorkspaceEvent
}

class WorkspaceViewModel @JvmOverloads constructor(
    application: Application,
    private val downloader: DocumentDownloader = AuthenticatedDocumentDownloader(application)
) : AndroidViewModel(application) {

    private val networkMonitor = NetworkMonitor(application)
    private var downloadJob: Job? = null

    private val _uiState = MutableStateFlow(
        WorkspaceUiState(
            isOnline = networkMonitor.refreshNow(),
            savedDocuments = downloader.listCachedDocuments()
        )
    )
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WorkspaceEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WorkspaceEvent> = _events.asSharedFlow()

    private var hasCompletedInitialLoad = false
    private var notificationPermissionPrompted = false

    init {
        networkMonitor.start()
        viewModelScope.launch {
            networkMonitor.isConnected.collect { connected ->
                _uiState.update { state ->
                    val nextError = when {
                        !connected && !hasCompletedInitialLoad -> ConnectionErrorKind.OFFLINE
                        connected && state.errorKind == ConnectionErrorKind.OFFLINE -> ConnectionErrorKind.NONE
                        else -> state.errorKind
                    }
                    state.copy(
                        isOnline = connected,
                        errorKind = nextError,
                        showSplash = if (!connected && !hasCompletedInitialLoad) false else state.showSplash
                    )
                }
            }
        }
    }

    override fun onCleared() {
        networkMonitor.stop()
        dismissJsDialog(confirmed = false)
        super.onCleared()
    }

    fun onPageStarted(canGoBack: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = true,
                loadProgress = it.loadProgress.coerceAtLeast(15),
                canGoBack = canGoBack,
                errorKind = if (it.errorKind == ConnectionErrorKind.SSL_BLOCKED) {
                    ConnectionErrorKind.NONE
                } else {
                    it.errorKind
                }
            )
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            it.copy(
                loadProgress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun onPageFinished(canGoBack: Boolean) {
        hasCompletedInitialLoad = true
        _uiState.update {
            it.copy(
                showSplash = false,
                isLoading = false,
                loadProgress = 100,
                isRefreshing = false,
                canGoBack = canGoBack
            )
        }
    }

    fun onWebViewScrollPositionChanged(isAtTop: Boolean) {
        if (_uiState.value.isWebViewAtTop != isAtTop) {
            _uiState.update { it.copy(isWebViewAtTop = isAtTop) }
        }
    }

    fun onMainFrameError(isOfflineHint: Boolean) {
        val online = networkMonitor.refreshNow()
        val kind = if (!online || isOfflineHint) {
            ConnectionErrorKind.OFFLINE
        } else {
            ConnectionErrorKind.CONNECTION_FAILED
        }
        _uiState.update {
            it.copy(
                showSplash = false,
                isLoading = false,
                isRefreshing = false,
                isOnline = online,
                errorKind = kind
            )
        }
    }

    fun onSslErrorBlocked() {
        _uiState.update {
            it.copy(
                showSplash = false,
                isLoading = false,
                isRefreshing = false,
                errorKind = ConnectionErrorKind.SSL_BLOCKED
            )
        }
    }

    fun onStartRefresh() {
        val online = networkMonitor.refreshNow()
        if (!online) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    isOnline = false,
                    errorKind = ConnectionErrorKind.OFFLINE
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isRefreshing = true,
                isLoading = true,
                errorKind = ConnectionErrorKind.NONE
            )
        }
    }

    fun onRetryConnection(): Boolean {
        val online = networkMonitor.refreshNow()
        if (!online) {
            _uiState.update {
                it.copy(
                    isOnline = false,
                    errorKind = ConnectionErrorKind.OFFLINE
                )
            }
            return false
        }
        _uiState.update {
            it.copy(
                isOnline = true,
                isLoading = true,
                loadProgress = 15,
                errorKind = ConnectionErrorKind.NONE
            )
        }
        return true
    }

    fun onExternalNavigationRequested(uri: Uri) {
        _events.tryEmit(WorkspaceEvent.OpenExternalUri(uri))
    }

    fun onUntrustedNavigationBlocked() {
        _events.tryEmit(WorkspaceEvent.ShowMessage(R.string.untrusted_link_blocked))
    }

    fun downloadAuthenticatedDocument(
        url: String,
        expectedMimeType: String,
        contentDispositionHint: String? = null,
        openPrintImmediately: Boolean = false
    ) {
        if (_uiState.value.isDownloading) {
            _events.tryEmit(WorkspaceEvent.ShowMessage(R.string.download_already_running))
            return
        }

        if (!notificationPermissionPrompted) {
            notificationPermissionPrompted = true
            _events.tryEmit(WorkspaceEvent.RequestNotificationPermissionIfNeeded)
        }

        val statusText = if (openPrintImmediately) {
            R.string.download_preparing_print
        } else {
            R.string.download_in_progress
        }

        _uiState.update {
            it.copy(
                isDownloading = true,
                downloadStatusResId = statusText,
                downloadProgress = null
            )
        }

        downloadJob = viewModelScope.launch {
            val outcome = downloader.download(
                url = url,
                expectedMimeType = expectedMimeType,
                contentDispositionHint = contentDispositionHint,
                openPrintImmediately = openPrintImmediately,
                onProgress = { progress ->
                    if (isActive) _uiState.update { it.copy(downloadProgress = progress) }
                }
            )
            // A cancel that raced with completion wins: never surface a result the user cancelled.
            ensureActive()
            when (outcome) {
                is DownloadOutcome.Success -> {
                    val refreshedList = downloader.listCachedDocuments()
                    DownloadNotificationHelper.showDownloadComplete(
                        getApplication(),
                        outcome.document
                    )
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = null,
                            savedDocuments = refreshedList,
                            activeDocument = if (outcome.openPrintImmediately) null else outcome.document
                        )
                    }
                    if (outcome.openPrintImmediately && outcome.document.isPdf) {
                        _events.tryEmit(WorkspaceEvent.PrintDocument(outcome.document))
                    }
                }

                DownloadOutcome.AuthRequired -> {
                    _uiState.update { it.copy(isDownloading = false, downloadProgress = null) }
                    _events.tryEmit(WorkspaceEvent.SessionExpired)
                }

                is DownloadOutcome.Failure -> {
                    _uiState.update { it.copy(isDownloading = false, downloadProgress = null) }
                    _events.tryEmit(WorkspaceEvent.ShowMessage(outcome.reasonResId))
                }
            }
        }
    }

    /** Stops the running download (the socket is torn down) and tells the user it was cancelled. */
    fun cancelDownload() {
        val job = downloadJob ?: return
        if (!_uiState.value.isDownloading) return
        job.cancel()
        downloadJob = null
        _uiState.update { it.copy(isDownloading = false, downloadProgress = null) }
        _events.tryEmit(WorkspaceEvent.ShowMessage(R.string.download_cancelled))
    }

    fun resetAccountState() {
        downloadJob?.cancel()
        downloadJob = null
        dismissJsDialog(confirmed = false)
        AuthenticatedDocumentDownloader.clearCachedDocuments(getApplication())
        _uiState.value = WorkspaceUiState(isOnline = _uiState.value.isOnline)
    }

    fun dismissActiveDocument() {
        _uiState.update { it.copy(activeDocument = null) }
    }

    fun openSavedDocumentsSheet() {
        val refreshedList = downloader.listCachedDocuments()
        _uiState.update {
            it.copy(
                savedDocuments = refreshedList,
                showSavedDocumentsSheet = true
            )
        }
    }

    fun dismissSavedDocumentsSheet() {
        _uiState.update { it.copy(showSavedDocumentsSheet = false) }
    }

    fun showJsDialog(request: JsDialogRequest) {
        _uiState.value.pendingJsDialog?.jsResult?.cancel()
        _uiState.update { it.copy(pendingJsDialog = request) }
    }

    fun dismissJsDialog(confirmed: Boolean) {
        val pending = _uiState.value.pendingJsDialog ?: return
        _uiState.update { it.copy(pendingJsDialog = null) }
        runCatching {
            if (confirmed) pending.jsResult.confirm() else pending.jsResult.cancel()
        }
    }
}
