package com.hfremovalsadelaide.invoices.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy

/**
 * Callbacks bridging the hardened Android WebView to the Jetpack Compose UI layer.
 */
interface InvoiceWebViewCallbacks {
    fun onPageStarted(canGoBack: Boolean)
    fun onProgressChanged(progress: Int)
    fun onPageFinished(canGoBack: Boolean)
    fun onScrollAtTopChanged(isAtTop: Boolean)
    fun onMainFrameError(isOfflineHint: Boolean)
    fun onSslErrorBlocked()
    fun onDownloadPdf(url: String, openPrintImmediately: Boolean)
    fun onDownloadCsv(url: String)
    fun onFallbackDownload(url: String, mimeType: String, contentDisposition: String?)
    fun onOpenExternalUri(uri: Uri)
    fun onUntrustedNavigationBlocked()
    fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        acceptTypes: Array<String>
    ): Boolean
    fun onJsAlert(message: String, result: JsResult): Boolean
    fun onJsConfirm(message: String, result: JsResult): Boolean
}

/**
 * Builds and hardens the Android WebView for the live HF Removals Adelaide Next.js application.
 *
 * Security controls enforced:
 * - HTTPS-only strict origin allowlist (hf-invoice-generator.vercel.app)
 * - Safe Browsing enabled
 * - First-party persistent cookies only (third-party cookies disabled)
 * - No JavaScript bridges (addJavascriptInterface is never called)
 * - No file:// or content:// access
 * - Mixed content strictly disallowed
 * - SSL errors always canceled, never ignored
 */
object InvoiceWebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        callbacks: InvoiceWebViewCallbacks
    ): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS

            configureCookies(this)
            configureSettings(settings)

            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                callbacks.onScrollAtTopChanged(scrollY <= 0)
            }

            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                val uri = Uri.parse(url)
                if (!TrustedOriginPolicy.isTrustedOrigin(uri)) {
                    callbacks.onUntrustedNavigationBlocked()
                    return@setDownloadListener
                }
                val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: "application/pdf"
                callbacks.onFallbackDownload(url, resolvedMime, contentDisposition)
            }

            webViewClient = createWebViewClient(callbacks)
            webChromeClient = createWebChromeClient(callbacks)

            loadUrl(TrustedOriginPolicy.PRODUCTION_URL)
        }
    }

    private fun configureCookies(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
        }
    }

    private fun createWebViewClient(callbacks: InvoiceWebViewCallbacks): WebViewClient {
        return object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return when (val decision = TrustedOriginPolicy.classifyNavigation(request.url)) {
                    TrustedOriginPolicy.NavigationDecision.AllowInWebView -> false
                    is TrustedOriginPolicy.NavigationDecision.DownloadPdf -> {
                        callbacks.onDownloadPdf(decision.url, decision.openPrintImmediately)
                        true
                    }
                    is TrustedOriginPolicy.NavigationDecision.DownloadCsv -> {
                        callbacks.onDownloadCsv(decision.url)
                        true
                    }
                    is TrustedOriginPolicy.NavigationDecision.OpenExternally -> {
                        callbacks.onOpenExternalUri(decision.uri)
                        true
                    }
                    TrustedOriginPolicy.NavigationDecision.Block -> {
                        callbacks.onUntrustedNavigationBlocked()
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                callbacks.onPageStarted(view.canGoBack())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                callbacks.onPageFinished(view.canGoBack())
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    val offlineHint = error.errorCode == ERROR_HOST_LOOKUP ||
                        error.errorCode == ERROR_CONNECT ||
                        error.errorCode == ERROR_TIMEOUT
                    callbacks.onMainFrameError(offlineHint)
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                // Never ignore or proceed past SSL certificate errors.
                handler.cancel()
                callbacks.onSslErrorBlocked()
            }
        }
    }

    private fun createWebChromeClient(callbacks: InvoiceWebViewCallbacks): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                callbacks.onProgressChanged(newProgress)
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                val acceptTypes = fileChooserParams.acceptTypes ?: emptyArray()
                return callbacks.onShowFileChooser(filePathCallback, acceptTypes)
            }

            override fun onJsAlert(
                view: WebView,
                url: String?,
                message: String?,
                result: JsResult
            ): Boolean {
                return callbacks.onJsAlert(message.orEmpty(), result)
            }

            override fun onJsConfirm(
                view: WebView,
                url: String?,
                message: String?,
                result: JsResult
            ): Boolean {
                return callbacks.onJsConfirm(message.orEmpty(), result)
            }
        }
    }
}
