package com.hfremovalsadelaide.invoices.jobs

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hfremovalsadelaide.invoices.auth.AuthRepository
import com.hfremovalsadelaide.invoices.offline.EncryptedStore
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Reminder scheduling with WorkManager. Delivery is NOT exact-to-the-minute: Android may defer work in Doze or
 * battery saver, so a reminder can arrive a few minutes late. Work is persisted by WorkManager, survives reboot,
 * and each reminder has a stable unique name, so it cannot be duplicated by a sync, restart or reboot.
 */
object JobsBackground {
    private const val PREFS = "hf_jobs"
    private const val KEY_ENABLED = "enabled_categories"
    private const val KEY_FINGERPRINTS = "fingerprints"
    private const val KEY_SCHEDULED = "scheduled_keys"
    private const val KEY_FIRED = "fired_keys"
    private const val PERIODIC = "hf_jobs_refresh"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun clearAccountState(c: Context) {
        val p = prefs(c)
        val wm = WorkManager.getInstance(c)
        p.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { wm.cancelUniqueWork(it) }
        p.edit().clear().apply()
        NotificationManagerCompat.from(c).cancelAll()
    }

    fun enabled(c: Context): Map<NotificationCategory, Boolean> {
        val raw = prefs(c).getString(KEY_ENABLED, null)
        val obj = runCatching { JSONObject(raw.orEmpty()) }.getOrDefault(JSONObject())
        return NotificationCategory.entries.associateWith { obj.optBoolean(it.serverKey, true) }
    }

    fun saveEnabled(c: Context, map: Map<NotificationCategory, Boolean>) {
        prefs(c).edit().putString(KEY_ENABLED, JSONObject().apply { map.forEach { (k, v) -> put(k.serverKey, v) } }.toString()).apply()
    }

    /** Call once at app start: refreshes jobs about every 30 minutes while there is a network connection. */
    fun start(c: Context) {
        JobNotifier.ensureChannel(c)
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Detects calendar/assignment/completion changes, then (re)plans the future reminders. */
    @Synchronized
    fun onSnapshot(c: Context, snapshot: JobsSnapshot, now: Instant = Instant.now()) {
        val p = prefs(c)
        val enabled = enabled(c)
        val old = readFingerprints(p.getString(KEY_FINGERPRINTS, null))
        ChangeDetector.diff(old, snapshot.jobs).forEach { change ->
            if (enabled[change.category] == false) return@forEach
            val job = snapshot.jobs.first { it.id == change.jobId }
            // The key includes the new start/state, so the same change is only ever announced once.
            val key = "change|${change.jobId}|${change.category.serverKey}|${job.scheduledStart?.epochSecond}|${job.state.server}|${job.cancelled}"
            if (markFired(p, key)) JobNotifier.show(c, key, change.category, job)
        }
        p.edit().putString(KEY_FINGERPRINTS, writeFingerprints(snapshot.jobs)).apply()

        val plan = ReminderPlanner.plan(snapshot.jobs, enabled, now)
        val wm = WorkManager.getInstance(c)
        val previous = p.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        (previous - plan.map { it.key }.toSet()).forEach { wm.cancelUniqueWork(it) }
        plan.forEach { r ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(now, r.at).toMillis().coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("key" to r.key, "jobId" to r.jobId, "category" to r.category.serverKey))
                .build()
            // KEEP: an already-queued identical reminder is left alone, so syncs never duplicate or reset it.
            wm.enqueueUniqueWork(r.key, ExistingWorkPolicy.KEEP, request)
        }
        p.edit().putStringSet(KEY_SCHEDULED, plan.map { it.key }.toSet()).apply()
    }

    internal fun markFired(p: android.content.SharedPreferences, key: String): Boolean {
        val fired = p.getStringSet(KEY_FIRED, emptySet()).orEmpty()
        if (key in fired) return false
        p.edit().putStringSet(KEY_FIRED, (fired + key).toList().takeLast(300).toSet()).apply()
        return true
    }

    internal fun fired(c: Context) = prefs(c)

    private fun writeFingerprints(jobs: List<Job>) = JSONObject().apply {
        jobs.forEach {
            put(it.id, JSONObject().put("start", it.scheduledStart?.toString()).put("cancelled", it.cancelled)
                .put("state", it.state.server).put("assignees", org.json.JSONArray(it.assignees.sorted())))
        }
    }.toString()

    private fun readFingerprints(raw: String?): Map<String, JobFingerprint> {
        val obj = runCatching { JSONObject(raw.orEmpty()) }.getOrNull() ?: return emptyMap()
        return obj.keys().asSequence().associateWith { id ->
            val o = obj.getJSONObject(id)
            JobFingerprint(
                start = o.optString("start", "").takeIf { it.isNotEmpty() && it != "null" }?.let { runCatching { Instant.parse(it) }.getOrNull() },
                cancelled = o.optBoolean("cancelled"), state = JobState.from(o.optString("state")),
                assignees = o.optJSONArray("assignees")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
            )
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = EncryptedStore(applicationContext)
        val owner = AuthRepository.readCachedUser(store) ?: return Result.success()
        return when (val r = JobsApi.snapshot()) {
            is JobsResult.Success -> {
                if (AuthRepository.readCachedUser(store) == owner) {
                    JobsBackground.onSnapshot(applicationContext, r.value)
                }
                Result.success()
            }
            JobsResult.AuthRequired -> Result.success()
            is JobsResult.Failure -> Result.retry()
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString("key") ?: return Result.success()
        val jobId = inputData.getString("jobId") ?: return Result.success()
        val category = NotificationCategory.entries.firstOrNull { it.serverKey == inputData.getString("category") } ?: return Result.success()
        val store = EncryptedStore(applicationContext)
        val owner = AuthRepository.readCachedUser(store)
        val prefs = JobsBackground.fired(applicationContext)
        if (JobsBackground.enabled(applicationContext)[category] == false) return Result.success()
        val job = when (val detail = JobsApi.detail(jobId)) {
            is JobsResult.Success -> detail.value
            JobsResult.AuthRequired -> return Result.success()
            is JobsResult.Failure -> {
                if (!detail.connectionUnavailable) return Result.success()
                null
            }
        }
        // Re-check the live state so a reminder never fires for a job that was cancelled, started or finished meanwhile.
        if (job != null) {
            if (job.cancelled) return Result.success()
            val stillRelevant = when (category) {
                NotificationCategory.RUNNING_LATE -> job.state == JobState.IN_PROGRESS || job.state == JobState.PAUSED
                else -> job.state == JobState.SCHEDULED
            }
            if (!stillRelevant) return Result.success()
        } else if (category == NotificationCategory.RUNNING_LATE) {
            return Result.success() // cannot confirm the job is still running; do not guess
        }
        if (AuthRepository.readCachedUser(store) != owner || !JobsBackground.markFired(prefs, key)) return Result.success()
        JobNotifier.show(applicationContext, key, category, job ?: placeholder(jobId))
        return Result.success()
    }

    private fun placeholder(id: String) = Job(
        id = id, title = "Job reminder", scheduledStart = null, scheduledEnd = null, dateOnly = false, serviceDate = null,
        bookingStatus = BookingStatus.UNKNOWN, cancelled = false, state = JobState.SCHEDULED, signoff = SignoffStatus.NONE,
        reference = null, customerName = null, customerPhone = null, pickup = null, destination = null, truck = null, crewSize = null,
        notes = null, assignees = emptyList(), actualStart = null, actualFinish = null, elapsedMinutes = 0, breakMinutes = 0, workingMinutes = 0
    )
}
