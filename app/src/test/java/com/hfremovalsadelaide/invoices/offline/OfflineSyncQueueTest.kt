package com.hfremovalsadelaide.invoices.offline

import com.hfremovalsadelaide.invoices.network.ApiResult
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineSyncQueueTest {

    private val xorCipher = object : CipherBox {
        override fun encrypt(plainText: String): ByteArray =
            plainText.toByteArray(Charsets.UTF_8).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun decrypt(cipherBlob: ByteArray): String? =
            String(cipherBlob.map { (it.toInt() xor 0x5A).toByte() }.toByteArray(), Charsets.UTF_8)
    }

    @Test
    fun `encrypted store never stores plaintext and wipes cleanly on clearAll`() {
        val ctx = RuntimeEnvironment.getApplication()
        val store = EncryptedStore(ctx, xorCipher)
        store.writeText("secret_item", "Sensitive Address 123")
        val rawFiles = ctx.filesDir.resolve("hf_secure_cache").listFiles().orEmpty()
        assertTrue(rawFiles.isNotEmpty())
        val onDisk = rawFiles.first { it.name.contains("secret_item") }.readText()
        assertNotEquals("Sensitive Address 123", onDisk)
        assertEquals("Sensitive Address 123", store.readText("secret_item"))

        store.clearAll()
        assertNull(store.readText("secret_item"))
    }

    @Test
    fun `offline queue only supports field operations and never financial issuance`() {
        val names = OfflineOpKind.entries.map { it.serverKind }.toSet()
        // stop_event records arriving at or leaving a pickup or delivery stop: a field operation, never financial.
        assertEquals(setOf("job_time_action", "condition_report", "inventory_scan", "job_photo", "stop_event"), names)
    }

    @Test
    fun `flush removes applied or deduped items and preserves conflicts for resolution`() = runTest {
        val ctx = RuntimeEnvironment.getApplication()
        val store = EncryptedStore(ctx, xorCipher).apply { clearAll() }
        var capturedPayload: JSONObject? = null
        val queue = OfflineSyncQueue(store) { body ->
            capturedPayload = body
            val ops = body.getJSONArray("operations")
            val results = JSONArray()
            for (i in 0 until ops.length()) {
                val key = ops.getJSONObject(i).getString("idempotencyKey")
                val status = when (i) {
                    0 -> "applied"
                    1 -> "deduped"
                    else -> "conflict"
                }
                results.put(
                    JSONObject()
                        .put("idempotencyKey", key)
                        .put("status", status)
                        .put("error", if (status == "conflict") "Newer server timestamp" else "")
                )
            }
            ApiResult.Success(JSONObject().put("results", results))
        }
        queue.bindAccount("admin")

        val op1 = queue.enqueue(OfflineOpKind.JOB_TIME_ACTION, "11111111-1111-4111-8111-111111111111", "Start", JSONObject().put("action", "start"), "2026-11-25T00:00:00Z")
        val op2 = queue.enqueue(OfflineOpKind.INVENTORY_SCAN, "11111111-1111-4111-8111-111111111111", "Scan", JSONObject().put("barcode", "BX1").put("status", "loaded"))
        val op3 = queue.enqueue(OfflineOpKind.JOB_TIME_ACTION, "11111111-1111-4111-8111-111111111111", "Finish", JSONObject().put("action", "finish"), "2026-11-25T00:00:00Z")
        assertEquals(3, queue.pendingCount)

        val res = queue.flush()
        assertTrue(res is ApiResult.Success)
        val summary = (res as ApiResult.Success).data
        assertEquals(1, summary.applied)
        assertEquals(1, summary.deduped)
        assertEquals(1, summary.conflicts)
        assertEquals(1, summary.remaining)
        assertEquals(0, queue.pendingCount)
        assertEquals(1, queue.conflictCount)
        assertEquals(op3.idempotencyKey, queue.operations.value.single().idempotencyKey)
        assertNotNullId(op1.idempotencyKey, op2.idempotencyKey, capturedPayload)
        assertEquals("admin", capturedPayload?.getString("principalId"))

        queue.bindAccount("other-account")
        assertTrue(queue.operations.value.isEmpty())
        queue.bindAccount("admin")
        assertEquals(op3.idempotencyKey, queue.operations.value.single().idempotencyKey)

        queue.dismiss(op3.idempotencyKey)
        assertEquals(0, queue.operations.value.size)
    }

    private fun assertNotNullId(k1: String, k2: String, payload: JSONObject?) {
        assertNotEquals(k1, k2)
        assertEquals(3, payload?.getJSONArray("operations")?.length())
    }
}
