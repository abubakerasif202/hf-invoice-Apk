package com.hfremovalsadelaide.invoices.pdf

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hfremovalsadelaide.invoices.R

/**
 * Posts download-complete notifications with Open and Share quick actions once an invoice PDF
 * or CSV export has been saved.
 */
object DownloadNotificationHelper {

    private const val CHANNEL_ID = "hf_invoice_downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showDownloadComplete(context: Context, document: DownloadedDocument) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val requestCode = document.fileName.hashCode() and 0x7FFFFFFF
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            PdfActions.buildOpenIntent(context, document),
            flags
        )

        val sharePendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 1,
            PdfActions.buildShareIntent(context, document),
            flags
        )

        val locationLine = if (document.savedToPublicDownloads) {
            context.getString(R.string.download_saved_to_downloads)
        } else {
            context.getString(R.string.download_saved_to_app_storage)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_invoice)
            .setColor(Color.parseColor("#073D2F"))
            .setContentTitle(context.getString(R.string.notification_download_complete_title))
            .setContentText("${document.fileName} · ${document.formattedSize}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${document.fileName} (${document.formattedSize})\n$locationLine")
            )
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(
                0,
                context.getString(R.string.notification_action_open),
                openPendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_share),
                sharePendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }
}
