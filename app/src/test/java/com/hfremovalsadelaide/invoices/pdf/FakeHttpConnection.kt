package com.hfremovalsadelaide.invoices.pdf

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Scriptable HttpURLConnection so the downloader can be tested without a network. */
class FakeHttpConnection(
    url: URL,
    private val status: Int = 200,
    headers: Map<String, String> = emptyMap(),
    private val bodyStream: () -> InputStream = { ByteArrayInputStream(ByteArray(0)) },
    private val failOnResponse: IOException? = null,
    private val announcedLength: Long = -1L
) : HttpURLConnection(url) {

    private val responseHeaders = headers.mapKeys { it.key.lowercase() }
    var disconnected = false
        private set

    override fun connect() = Unit
    override fun usingProxy() = false
    override fun disconnect() {
        disconnected = true
    }

    override fun getResponseCode(): Int {
        failOnResponse?.let { throw it }
        return status
    }

    override fun getInputStream(): InputStream = bodyStream()
    override fun getHeaderField(name: String?): String? = name?.lowercase()?.let(responseHeaders::get)
    override fun getContentType(): String? = getHeaderField("content-type")
    override fun getContentLengthLong(): Long = announcedLength
}
