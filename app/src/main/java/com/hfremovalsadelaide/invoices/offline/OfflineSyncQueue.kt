package com.hfremovalsadelaide.invoices.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hfremovalsadelaide.invoices.auth.AuthRepository
import com.hfremovalsadelaide.invoices.network.ApiClient
import com.hfremovalsadelaide.invoices.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class OfflineOpKind(val serverKind: String) {
    JOB_TIME_ACTION("job_time_action"),
    CONDITION_REPORT("condition_report"),
    INVENTORY_SCAN("inventory_scan"),
    JOB_PHOTO("job_photo")
}

enum class QueueOpStatus { PENDING, CONFLICT, REJECTED }

data class QueuedOperation(
    val idempotencyKey: String,
    val kind: OfflineOpKind,
    val jobId: String,
    val label: String,
    val payload: JSONObject,
    val deviceRecordedAt: String,
    val expectedUpdatedAt: String? = null,
    val status: QueueOpStatus = QueueOpStatus.PENDING,
    val error: String? = null
)

data class SyncFlushSummary(
    val applied: Int = 0,
    val deduped: Int = 0,
    val conflicts: Int = 0,
    val rejected: Int = 0,
    val remaining: Int = 0
)

/**
 * Queues safe field operations offline with encrypted persistence, device timestamps, and UUID idempotency keys.
 * Financial invoice issuance and payments are intentionally excluded and can never be queued offline.
 */
class OfflineSyncQueue(
    private val store: EncryptedStore,
    private val sender: suspend (JSONObject) -> ApiResult<JSONObject> = { payload ->
        ApiClient.post("/api/sync", payload) { JSONObject(it) }
    }
) {
    private var ownerId: String? = null
    private var generation = 0
    private val _operations = MutableStateFlow<List<QueuedOperation>>(emptyList())
    val operations: StateFlow<List<QueuedOperation>> = _operations.asStateFlow()

    @Synchronized
    fun bindAccount(id: String?) {
        if (ownerId == id) return
        generation++
        ownerId = id
        _operations.value = id?.let { load(it) }.orEmpty()
    }

    val pendingCount: Int
        get() = _operations.value.count { it.status == QueueOpStatus.PENDING }

    val conflictCount: Int
        get() = _operations.value.count { it.status == QueueOpStatus.CONFLICT || it.status == QueueOpStatus.REJECTED }

    @Synchronized
    fun enqueue(
        kind: OfflineOpKind,
        jobId: String,
        label: String,
        payload: JSONObject,
        expectedUpdatedAt: String? = null,
        deviceRecordedAt: String = Instant.now().toString(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): QueuedOperation {
        checkNotNull(ownerId) { "Sign in before queueing an offline update." }
        val op = QueuedOperation(
            idempotencyKey = idempotencyKey,
            kind = kind,
            jobId = jobId,
            label = label,
            payload = payload,
            deviceRecordedAt = deviceRecordedAt,
            expectedUpdatedAt = expectedUpdatedAt
        )
        val next = _operations.value + op
        save(next)
        return op
    }

    @Synchronized
    fun dismiss(idempotencyKey: String) {
        save(_operations.value.filterNot { it.idempotencyKey == idempotencyKey })
    }

    @Synchronized
    fun clear() {
        generation++
        ownerId?.let { store.delete(queueKey(it)) }
        _operations.value = emptyList()
    }

    suspend fun flush(): ApiResult<SyncFlushSummary> {
        val (owner, pending, startedAt) = synchronized(this) {
            Triple(ownerId, _operations.value.filter { it.status == QueueOpStatus.PENDING }, generation)
        }
        if (owner == null) return ApiResult.AuthRequired
        if (pending.isEmpty()) return ApiResult.Success(SyncFlushSummary(remaining = _operations.value.size))

        val batch = JSONArray()
        pending.take(50).forEach { op ->
            val obj = JSONObject(op.payload.toString())
            obj.put("idempotencyKey", op.idempotencyKey)
            obj.put("deviceRecordedAt", op.deviceRecordedAt)
            obj.put("kind", op.kind.serverKind)
            obj.put("jobId", op.jobId)
            if (op.expectedUpdatedAt != null && op.kind == OfflineOpKind.JOB_TIME_ACTION) {
                obj.put("expectedUpdatedAt", op.expectedUpdatedAt)
            }
            batch.put(obj)
        }

        return when (val res = sender(JSONObject().put("principalId", owner).put("operations", batch))) {
            is ApiResult.Success -> synchronized(this) {
                if (generation != startedAt) return@synchronized ApiResult.AuthRequired
                val results = res.data.optJSONArray("results") ?: JSONArray()
                val byKey = mutableMapOf<String, Pair<String, String?>>()
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val key = item.optString("idempotencyKey")
                    val status = item.optString("status")
                    val err = item.optString("error").takeIf { it.isNotBlank() }
                    if (key.isNotBlank()) byKey[key] = status to err
                }
                var applied = 0
                var deduped = 0
                var conflicts = 0
                var rejected = 0
                val remainingList = _operations.value.mapNotNull { op ->
                    val outcome = byKey[op.idempotencyKey] ?: return@mapNotNull op
                    when (outcome.first) {
                        "applied" -> { applied++; null }
                        "deduped" -> { deduped++; null }
                        "conflict" -> {
                            conflicts++
                            op.copy(status = QueueOpStatus.CONFLICT, error = outcome.second ?: "Server has newer record")
                        }
                        else -> {
                            rejected++
                            op.copy(status = QueueOpStatus.REJECTED, error = outcome.second ?: "Rejected by server")
                        }
                    }
                }
                save(remainingList)
                ApiResult.Success(SyncFlushSummary(applied, deduped, conflicts, rejected, remainingList.size))
            }
            ApiResult.AuthRequired -> ApiResult.AuthRequired
            is ApiResult.Forbidden -> ApiResult.Forbidden(res.message)
            is ApiResult.NetworkError -> ApiResult.NetworkError(res.message)
            is ApiResult.ServerError -> ApiResult.ServerError(res.code, res.message)
        }
    }

    private fun save(list: List<QueuedOperation>) {
        _operations.value = list
        val arr = JSONArray()
        list.forEach { op ->
            arr.put(
                JSONObject()
                    .put("idempotencyKey", op.idempotencyKey)
                    .put("kind", op.kind.name)
                    .put("jobId", op.jobId)
                    .put("label", op.label)
                    .put("payload", op.payload)
                    .put("deviceRecordedAt", op.deviceRecordedAt)
                    .put("expectedUpdatedAt", op.expectedUpdatedAt ?: JSONObject.NULL)
                    .put("status", op.status.name)
                    .put("error", op.error ?: JSONObject.NULL)
            )
        }
        ownerId?.let { store.writeText(queueKey(it), arr.toString()) }
    }

    private fun load(owner: String): List<QueuedOperation> {
        val raw = store.readText(queueKey(owner)) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                QueuedOperation(
                    idempotencyKey = o.getString("idempotencyKey"),
                    kind = OfflineOpKind.valueOf(o.getString("kind")),
                    jobId = o.getString("jobId"),
                    label = o.optString("label", "Offline update"),
                    payload = o.getJSONObject("payload"),
                    deviceRecordedAt = o.getString("deviceRecordedAt"),
                    expectedUpdatedAt = o.optString("expectedUpdatedAt", "").takeIf { it.isNotBlank() && it != "null" },
                    status = runCatching { QueueOpStatus.valueOf(o.optString("status")) }.getOrDefault(QueueOpStatus.PENDING),
                    error = o.optString("error", "").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val WORK_NAME = "hf_offline_sync"
        private var applicationQueue: OfflineSyncQueue? = null

        private fun queueKey(owner: String) = "offline_queue_$owner"

        @Synchronized
        fun forApplication(context: Context): OfflineSyncQueue {
            applicationQueue?.let { return it }
            val store = EncryptedStore(context.applicationContext)
            return OfflineSyncQueue(store).also {
                it.bindAccount(AuthRepository.readCachedUser(store)?.id)
                applicationQueue = it
            }
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun scheduleSync(context: Context) {
            val req = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val queue = OfflineSyncQueue.forApplication(applicationContext)
        if (queue.pendingCount == 0) return Result.success()
        return when (queue.flush()) {
            is ApiResult.Success, ApiResult.AuthRequired, is ApiResult.Forbidden -> Result.success()
            is ApiResult.NetworkError, is ApiResult.ServerError -> Result.retry()
        }
    }
}
