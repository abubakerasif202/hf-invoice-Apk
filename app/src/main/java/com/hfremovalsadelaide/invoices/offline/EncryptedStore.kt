package com.hfremovalsadelaide.invoices.offline

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CipherBox {
    fun encrypt(plainText: String): ByteArray
    fun decrypt(cipherBlob: ByteArray): String?
}

/**
 * Hardware/OS-backed AES-256-GCM encryption via AndroidKeyStore.
 * Sensitive cached jobs, user session metadata, and offline queues are never stored in plaintext.
 */
class AndroidKeystoreCipherBox(
    private val alias: String = "hf_removals_offline_key_v1"
) : CipherBox {

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plainText: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return ByteBuffer.allocate(4 + iv.size + body.size)
            .putInt(iv.size)
            .put(iv)
            .put(body)
            .array()
    }

    override fun decrypt(cipherBlob: ByteArray): String? = runCatching {
        val buf = ByteBuffer.wrap(cipherBlob)
        val ivLen = buf.int
        if (ivLen !in 12..32 || buf.remaining() <= ivLen) return null
        val iv = ByteArray(ivLen).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(body), StandardCharsets.UTF_8)
    }.getOrNull()
}

class EncryptedStore(
    context: Context,
    private val cipherBox: CipherBox = AndroidKeystoreCipherBox()
) {
    private val dir = File(context.filesDir, "hf_secure_cache").apply { mkdirs() }

    @Synchronized
    fun writeText(name: String, content: String) {
        val safeName = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val target = File(dir, "$safeName.enc")
        runCatching {
            val encrypted = cipherBox.encrypt(content)
            target.writeBytes(encrypted)
        }
    }

    @Synchronized
    fun readText(name: String): String? {
        val safeName = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val target = File(dir, "$safeName.enc")
        if (!target.exists()) return null
        return runCatching { cipherBox.decrypt(target.readBytes()) }.getOrNull()
    }

    @Synchronized
    fun delete(name: String) {
        val safeName = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        runCatching { File(dir, "$safeName.enc").delete() }
    }

    /** Wipes every encrypted offline cache and queued payload on logout. */
    @Synchronized
    fun clearAll() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }
}
