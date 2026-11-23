package com.hfremovalsadelaide.invoices.jobs

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hfremovalsadelaide.invoices.MainActivity
import com.hfremovalsadelaide.invoices.R

/** Job notifications. Lock screen shows only a generic public version; phone numbers/addresses are never included. */
object JobNotifier {
    const val EXTRA_JOB_ID = "hf_job_id"
    private const val CHANNEL_ID = "hf_job_reminders"

    /** Tapping a job notification opens the app straight to that job. */
    fun openIntent(context: Context, jobId: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_JOB_ID, jobId)
        }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Job reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Upcoming jobs, schedule changes and sign-off reminders"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, key: String, category: NotificationCategory, job: Job) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = openIntent(context, job.id)
        val pending = PendingIntent.getActivity(context, key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_invoice)
            .setContentTitle(ReminderText.publicVersion(category))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_invoice)
            .setColor(Color.parseColor("#073D2F"))
            .setContentTitle(job.title.ifBlank { "Job" })
            .setContentText(ReminderText.body(category, job))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        try {
            manager.notify(key.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }
}
