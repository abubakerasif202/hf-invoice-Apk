package com.hfremovalsadelaide.invoices.pdf

import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import java.net.URI

/**
 * Pure decision logic for an HTTP response to an authenticated document request.
 *
 * The downloader never follows redirects (the session cookie must only ever go to the original,
 * verified URL), so a redirect is interpreted here: a bounce to another page of the trusted site
 * means the server wants the user to sign in; anything else is blocked.
 */
object DownloadResponsePolicy {

    sealed interface Verdict {
        data object Proceed : Verdict
        data object SessionExpired : Verdict
        data class Failed(val reasonResId: Int) : Verdict
    }

    fun classify(
        requestUrl: String,
        status: Int,
        location: String?,
        contentType: String?
    ): Verdict = when (status) {
        401, 403 -> Verdict.SessionExpired
        in 300..399 -> classifyRedirect(requestUrl, location)
        404, 410 -> Verdict.Failed(R.string.download_failed_not_found)
        408, 504 -> Verdict.Failed(R.string.download_failed_timeout)
        in 500..599 -> Verdict.Failed(R.string.download_failed_server)
        in 200..299 ->
            // A login page served with HTTP 200 instead of the requested PDF/CSV.
            if (isHtml(contentType)) Verdict.SessionExpired else Verdict.Proceed
        else -> Verdict.Failed(R.string.download_failed_generic)
    }

    private fun classifyRedirect(requestUrl: String, location: String?): Verdict {
        val target = resolve(requestUrl, location)
            ?: return Verdict.Failed(R.string.download_failed_redirect)
        return if (TrustedOriginPolicy.isTrustedOrigin(target)) {
            Verdict.SessionExpired
        } else {
            Verdict.Failed(R.string.download_failed_redirect)
        }
    }

    private fun resolve(base: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        return runCatching { URI(base).resolve(location.trim()).toString() }.getOrNull()
    }

    private fun isHtml(contentType: String?): Boolean =
        contentType?.substringBefore(';')?.trim()?.equals("text/html", ignoreCase = true) == true
}
