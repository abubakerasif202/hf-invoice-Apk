package com.hfremovalsadelaide.invoices.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.security.TrustedOriginPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Downloads authenticated invoice PDFs and CSV exports from the trusted HF Removals origin.
 * Session cookies are read in memory from WebView's CookieManager, sent exclusively over HTTPS
 * to the verified host, and never logged or written to storage.
 */
class AuthenticatedDocumentDownloader(
    private val context: Context,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val cookieProvider: (String) -> String? = { CookieManager.getInstance().getCookie(it) }
) : DocumentDownloader {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_DOCUMENT_BYTES = 25 * 1024 * 1024 // 25 MB safety cap
        private const val MAX_CACHED_DOCUMENTS = 25
        private const val PUBLIC_SUBFOLDER = "HF Removals Invoices"
        private const val CACHE_DIR_NAME = "invoices"
        private const val PRIVATE_DIR_NAME = "private"
        private val cacheLock = Any()
        private val PDF_MAGIC_HEADER = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-

        fun clearCachedDocuments(context: Context) = synchronized(cacheLock) {
            val dir = File(context.cacheDir, CACHE_DIR_NAME)
            dir.walkBottomUp().filter { it.isFile }.forEach {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            dir.deleteRecursively()
        }
    }

    override suspend fun download(
        url: String,
        expectedMimeType: String,
        contentDispositionHint: String?,
        openPrintImmediately: Boolean,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadOutcome = coroutineScope {
        // Blocking socket reads do not respond to coroutine cancellation by themselves. This
        // watcher disconnects the connection the moment the caller cancels, which unblocks the
        // read immediately instead of waiting for the read timeout.
        val activeConnection = AtomicReference<HttpURLConnection?>(null)
        val watcher = launch(Dispatchers.IO) {
            try {
                awaitCancellation()
            } finally {
                activeConnection.get()?.disconnect()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                fetch(url, expectedMimeType, contentDispositionHint, openPrintImmediately, onProgress, activeConnection)
            }
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun fetch(
        url: String,
        expectedMimeType: String,
        contentDispositionHint: String?,
        openPrintImmediately: Boolean,
        onProgress: (DownloadProgress) -> Unit,
        activeConnection: AtomicReference<HttpURLConnection?>
    ): DownloadOutcome {
        if (!TrustedOriginPolicy.isTrustedOrigin(url)) {
            return DownloadOutcome.Failure(R.string.untrusted_link_blocked)
        }

        val isPdfExpected = expectedMimeType.equals("application/pdf", ignoreCase = true) ||
            TrustedOriginPolicy.isPdfDownloadUrl(android.net.Uri.parse(url))

        var connection: HttpURLConnection? = null
        try {
            val requestUrl = url
            connection = connectionFactory(URL(requestUrl)).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", if (isPdfExpected) "application/pdf" else "*/*")
                val sessionCookie = cookieProvider(requestUrl)
                if (!sessionCookie.isNullOrBlank()) {
                    setRequestProperty("Cookie", sessionCookie)
                }
            }
            activeConnection.set(connection)
            currentCoroutineContext().ensureActive()

            val statusCode = connection.responseCode
            when (
                val verdict = DownloadResponsePolicy.classify(
                    requestUrl = requestUrl,
                    status = statusCode,
                    location = connection.getHeaderField("Location"),
                    contentType = connection.contentType
                )
            ) {
                DownloadResponsePolicy.Verdict.SessionExpired -> return DownloadOutcome.AuthRequired
                is DownloadResponsePolicy.Verdict.Failed -> return DownloadOutcome.Failure(verdict.reasonResId)
                DownloadResponsePolicy.Verdict.Proceed -> Unit
            }

            val announced = connection.contentLengthLong.takeIf { it >= 0 }
            if (announced != null && announced > MAX_DOCUMENT_BYTES) {
                return DownloadOutcome.Failure(R.string.download_failed_too_large)
            }

            onProgress(DownloadProgress(0L, announced))
            val responseBytes = connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val out = ByteArrayOutputStream(announced?.toInt() ?: 32 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_DOCUMENT_BYTES) {
                        return DownloadOutcome.Failure(R.string.download_failed_too_large)
                    }
                    out.write(buffer, 0, read)
                    onProgress(DownloadProgress(total, announced))
                }
                out.toByteArray()
            }
            currentCoroutineContext().ensureActive()

            if (responseBytes.isEmpty()) {
                return DownloadOutcome.Failure(R.string.download_failed_generic)
            }

            if (isPdfExpected && !hasPdfMagicHeader(responseBytes)) {
                return DownloadOutcome.Failure(R.string.download_failed_invalid_pdf)
            }

            val headerDisposition = connection.getHeaderField("Content-Disposition")
                ?: contentDispositionHint
            val resolvedMime = when {
                isPdfExpected -> "application/pdf"
                else -> connection.contentType?.substringBefore(';')?.trim()
                    ?.takeIf { it.isNotBlank() } ?: expectedMimeType
            }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fallbackName = if (isPdfExpected) {
                "HF-Invoice-$timestamp.pdf"
            } else {
                "HF-Invoices-$timestamp.csv"
            }
            val requiredExt = if (isPdfExpected) ".pdf" else ".csv"
            val sanitizedName = TrustedOriginPolicy.extractAndSanitizeFilename(
                contentDisposition = headerDisposition,
                fallbackName = fallbackName,
                requiredExtension = requiredExt
            )

            val isPrivate = TrustedOriginPolicy.isPrivateDocumentUrl(url)
            val downloadContext = currentCoroutineContext()
            val localFile = try {
                synchronized(cacheLock) {
                    downloadContext.ensureActive()
                    writePrivateShareCopy(sanitizedName, responseBytes, isPrivate)
                }
            } catch (_: IOException) {
                return DownloadOutcome.Failure(R.string.download_failed_storage)
            }
            val savedPublic = !isPrivate && saveToDownloads(sanitizedName, resolvedMime, responseBytes)

            pruneOldCacheFiles()

            return DownloadOutcome.Success(
                document = DownloadedDocument(
                    fileName = sanitizedName,
                    mimeType = resolvedMime,
                    localFile = localFile,
                    sizeBytes = responseBytes.size.toLong(),
                    savedToPublicDownloads = savedPublic,
                    isPrivate = isPrivate
                ),
                openPrintImmediately = openPrintImmediately
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A cancel tears the socket down on purpose; report it as cancellation, not an error.
            currentCoroutineContext().ensureActive()
            return DownloadOutcome.Failure(networkFailureMessage(e))
        } finally {
            connection?.disconnect()
        }
    }

    private fun networkFailureMessage(error: Exception): Int = when (error) {
        is SocketTimeoutException -> R.string.download_failed_timeout
        is UnknownHostException, is ConnectException, is NoRouteToHostException,
        is SSLException -> R.string.download_failed_network
        else -> R.string.download_failed_generic
    }

    override fun listCachedDocuments(): List<DownloadedDocument> {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) return emptyList()
        return cacheDir.walkTopDown().filter {
            it.isFile && (it.name.endsWith(".pdf", true) || it.name.endsWith(".csv", true))
        }.sortedByDescending { it.lastModified() }.map { file ->
                val isPdf = file.name.endsWith(".pdf", true)
                val isPrivate = file.parentFile?.name == PRIVATE_DIR_NAME ||
                    file.name.startsWith("HF-Job-Completion-", true) ||
                    file.name.startsWith("HF-Inventory-", true) ||
                    file.name.startsWith("HF-Payment-Statement-", true)
                DownloadedDocument(
                    fileName = file.name,
                    mimeType = if (isPdf) "application/pdf" else "text/csv",
                    localFile = file,
                    sizeBytes = file.length(),
                    savedToPublicDownloads = !isPrivate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                    downloadedAtMillis = file.lastModified(),
                    isPrivate = isPrivate
                )
            }.toList()
    }

    private fun hasPdfMagicHeader(bytes: ByteArray): Boolean {
        if (bytes.size < PDF_MAGIC_HEADER.size) return false
        for (i in PDF_MAGIC_HEADER.indices) {
            if (bytes[i] != PDF_MAGIC_HEADER[i]) return false
        }
        return true
    }

    private fun writePrivateShareCopy(fileName: String, bytes: ByteArray, isPrivate: Boolean): File {
        val cache = File(context.cacheDir, CACHE_DIR_NAME)
        val dir = (if (isPrivate) File(cache, PRIVATE_DIR_NAME) else cache).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    /**
     * Saves the downloaded invoice into the device's Downloads collection via MediaStore on
     * Android 10+ (zero permissions required), or app-scoped Downloads storage on API 24-28.
     */
    private fun saveToDownloads(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(fileName, mimeType, bytes)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, fileName)
            runCatching {
                target.writeBytes(bytes)
                false
            }.getOrDefault(false)
        }
    }

    private fun saveViaMediaStore(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_SUBFOLDER"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return false

        return try {
            resolver.openOutputStream(itemUri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: throw IllegalStateException("Unable to open MediaStore output stream")

            val commitValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, commitValues, null, null)
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(itemUri, null, null) }
            false
        }
    }

    private fun pruneOldCacheFiles() {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        val files = dir.walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.toList()
        if (files.size > MAX_CACHED_DOCUMENTS) {
            files.drop(MAX_CACHED_DOCUMENTS).forEach { runCatching { it.delete() } }
        }
    }
}
