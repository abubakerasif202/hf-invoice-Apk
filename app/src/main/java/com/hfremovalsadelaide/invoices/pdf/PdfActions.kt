package com.hfremovalsadelaide.invoices.pdf

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.hfremovalsadelaide.invoices.R

/**
 * Provides safe, FileProvider-backed native Open, Share, Print, and external browser intents.
 */
object PdfActions {

    fun fileProviderUri(context: Context, document: DownloadedDocument): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, document.localFile)
    }

    fun buildOpenIntent(context: Context, document: DownloadedDocument): Intent {
        val contentUri = fileProviderUri(context, document)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, document.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareIntent(context: Context, document: DownloadedDocument): Intent {
        val contentUri = fileProviderUri(context, document)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = document.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, document.fileName.substringBeforeLast('.'))
            clipData = ClipData.newUri(context.contentResolver, document.fileName, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(
            sendIntent,
            context.getString(R.string.share_chooser_title)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openDocument(context: Context, document: DownloadedDocument): Boolean {
        return runCatching {
            context.startActivity(buildOpenIntent(context, document))
            true
        }.getOrDefault(false)
    }

    fun shareDocument(context: Context, document: DownloadedDocument): Boolean {
        return runCatching {
            context.startActivity(buildShareIntent(context, document))
            true
        }.getOrDefault(false)
    }

    fun printPdf(activity: Activity, document: DownloadedDocument): Boolean {
        if (!document.isPdf || !document.localFile.exists()) return false
        return runCatching {
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = document.fileName.substringBeforeLast('.')
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
            printManager.print(
                jobName,
                PdfPrintAdapter(document.localFile, document.fileName),
                attributes
            )
            true
        }.getOrDefault(false)
    }

    fun openExternalUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
