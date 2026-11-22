package com.hfremovalsadelaide.invoices.pdf

/** Download progress. [totalBytes] is null when the server did not announce a length. */
data class DownloadProgress(val bytesRead: Long, val totalBytes: Long?) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toFloat() / it).coerceIn(0f, 1f) }
}

/** Seam between the ViewModel and the network/storage implementation, so both can be tested. */
interface DocumentDownloader {
    suspend fun download(
        url: String,
        expectedMimeType: String,
        contentDispositionHint: String? = null,
        openPrintImmediately: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {}
    ): DownloadOutcome

    fun listCachedDocuments(): List<DownloadedDocument>
}
