package com.hfremovalsadelaide.invoices.pdf

import java.io.File

/**
 * Represents a verified invoice PDF or CSV export downloaded securely from the
 * HF Removals Adelaide backend.
 */
data class DownloadedDocument(
    val fileName: String,
    val mimeType: String,
    val localFile: File,
    val sizeBytes: Long,
    val savedToPublicDownloads: Boolean,
    val downloadedAtMillis: Long = System.currentTimeMillis(),
    /** Documents holding signatures or customer details are kept in app-private storage; export is an explicit Share. */
    val isPrivate: Boolean = false
) {
    val isPdf: Boolean
        get() = mimeType.equals("application/pdf", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true)

    val formattedSize: String
        get() = when {
            sizeBytes < 1024L -> "$sizeBytes B"
            sizeBytes < 1024L * 1024L -> "${sizeBytes / 1024L} KB"
            else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

sealed interface DownloadOutcome {
    data class Success(
        val document: DownloadedDocument,
        val openPrintImmediately: Boolean
    ) : DownloadOutcome

    data object AuthRequired : DownloadOutcome

    data class Failure(val reasonResId: Int) : DownloadOutcome
}
