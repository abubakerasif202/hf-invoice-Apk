package com.hfremovalsadelaide.invoices.security

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Enforces strict origin allowlisting, HTTPS-only navigation, document endpoint detection,
 * and safe filename extraction for the HF Removals Adelaide invoice application.
 */
object TrustedOriginPolicy {

    const val PRODUCTION_HOST = "hf-invoice-generator.vercel.app"
    const val PRODUCTION_URL = "https://$PRODUCTION_HOST"

    private val EXTERNAL_ACTION_SCHEMES = setOf("mailto", "tel")

    sealed interface NavigationDecision {
        /** Allow the WebView to load this trusted HTTPS URL. */
        data object AllowInWebView : NavigationDecision

        /** Intercept as an authenticated PDF download or print request. */
        data class DownloadPdf(val url: String, val openPrintImmediately: Boolean) : NavigationDecision

        /** Intercept as an authenticated CSV export download. */
        data class DownloadCsv(val url: String) : NavigationDecision

        /** Open an unrelated HTTPS or mailto/tel link in the Android system browser/handler. */
        data class OpenExternally(val uri: Uri) : NavigationDecision

        /** Block insecure schemes (http, file, content, javascript, intent, etc.). */
        data object Block : NavigationDecision
    }

    fun isTrustedOrigin(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val port = uri.port
        return scheme == "https" &&
            host == PRODUCTION_HOST &&
            (port == -1 || port == 443)
    }

    fun isTrustedOrigin(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return isTrustedOrigin(Uri.parse(url))
    }

    fun isPdfDownloadUrl(uri: Uri?): Boolean {
        if (!isTrustedOrigin(uri)) return false
        val path = uri?.path ?: return false
        return path.startsWith("/api/pdf/") && path.length > "/api/pdf/".length
    }

    fun isInlinePrintRequest(uri: Uri?): Boolean {
        if (!isPdfDownloadUrl(uri)) return false
        val query = uri?.encodedQuery ?: return false
        return query == "inline" ||
            query.startsWith("inline&") ||
            query.endsWith("&inline") ||
            query.contains("&inline&") ||
            uri.getQueryParameter("inline") != null
    }

    fun isCsvExportUrl(uri: Uri?): Boolean {
        if (!isTrustedOrigin(uri)) return false
        val path = uri?.path ?: return false
        return path == "/api/export" || path == "/api/export/"
    }

    fun classifyNavigation(uri: Uri?): NavigationDecision {
        if (uri == null) return NavigationDecision.Block
        val scheme = uri.scheme?.lowercase() ?: return NavigationDecision.Block

        if (isPdfDownloadUrl(uri)) {
            return NavigationDecision.DownloadPdf(
                url = uri.toString(),
                openPrintImmediately = isInlinePrintRequest(uri)
            )
        }

        if (isCsvExportUrl(uri)) {
            return NavigationDecision.DownloadCsv(url = uri.toString())
        }

        if (isTrustedOrigin(uri)) {
            return NavigationDecision.AllowInWebView
        }

        if (scheme == "https" && !uri.host.isNullOrBlank()) {
            return NavigationDecision.OpenExternally(uri)
        }

        if (scheme in EXTERNAL_ACTION_SCHEMES) {
            return NavigationDecision.OpenExternally(uri)
        }

        return NavigationDecision.Block
    }

    /**
     * Parses a Content-Disposition header (supporting RFC 5987 filename*=UTF-8''... and
     * standard filename="...") and returns a sanitized, path-safe filename.
     */
    fun extractAndSanitizeFilename(
        contentDisposition: String?,
        fallbackName: String,
        requiredExtension: String
    ): String {
        val rawName = parseContentDispositionFilename(contentDisposition) ?: fallbackName
        val cleaned = rawName
            .replace('\\', '_')
            .replace('/', '_')
            .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_")
            .trim()
            .trimStart('.')
            .take(120)

        val base = if (cleaned.isBlank()) fallbackName else cleaned
        val normalizedExt = if (requiredExtension.startsWith(".")) {
            requiredExtension.lowercase()
        } else {
            ".${requiredExtension.lowercase()}"
        }
        return if (base.lowercase().endsWith(normalizedExt)) {
            base
        } else {
            "$base$normalizedExt"
        }
    }

    private fun parseContentDispositionFilename(header: String?): String? {
        if (header.isNullOrBlank()) return null

        val rfc5987Match = Regex(
            """filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""",
            RegexOption.IGNORE_CASE
        ).find(header)
        if (rfc5987Match != null) {
            val encoded = rfc5987Match.groupValues[1].trim().trim('"')
            return runCatching {
                URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
            }.getOrDefault(encoded)
        }

        val quotedMatch = Regex(
            """filename\s*=\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE
        ).find(header)
        if (quotedMatch != null) {
            return quotedMatch.groupValues[1].trim()
        }

        val plainMatch = Regex(
            """filename\s*=\s*([^;\s]+)""",
            RegexOption.IGNORE_CASE
        ).find(header)
        return plainMatch?.groupValues?.getOrNull(1)?.trim()?.trim('"')
    }
}
