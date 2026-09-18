package com.mccal.folio.market

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** One HTTPS GET. Tests use a fake; the app uses [UrlHttpClient]. */
interface HttpClient {
    /**
     * Gets [url], reading at most [maxBytes]. [etag] sends `If-None-Match`, which is how Folio stays inside GitHub's
     * rate limits: a source that hasn't changed answers 304 with no body.
     */
    fun get(url: String, maxBytes: Int, etag: String? = null): HttpResult
}

sealed interface HttpResult {
    data class Body(val bytes: ByteArray, val etag: String?) : HttpResult {
        override fun equals(other: Any?) = other is Body && other.bytes.contentEquals(bytes) && other.etag == etag
        override fun hashCode() = 31 * bytes.contentHashCode() + etag.hashCode()
    }

    /** The cached copy is still current (HTTP 304). */
    data object NotModified : HttpResult

    /** The file is bigger than the caller allows, so it was never read into memory. */
    data object TooLarge : HttpResult

    /** Nothing was downloaded. [status] is the HTTP status when there was one. */
    data class Failed(val message: String, val status: Int? = null) : HttpResult {
        /** The source isn't there any more: 404, or 410 when the host says it's gone for good. */
        val missing: Boolean get() = status == 404 || status == 410
    }
}

/**
 * `HttpsURLConnection`, locked down for sources: HTTPS only, no cookies, no caching, a plain `User-Agent: Folio`
 * (T13: a source can't tell users apart), a byte cap, and redirects only to other HTTPS URLs.
 */
class UrlHttpClient(
    private val timeoutMs: Int = 15_000,
    private val maxRedirects: Int = 3,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : HttpClient {

    override fun get(url: String, maxBytes: Int, etag: String?): HttpResult {
        var target = url
        repeat(maxRedirects + 1) { hop ->
            val parsed = try {
                URL(target)
            } catch (e: Exception) {
                return HttpResult.Failed("that isn't a link Folio can open")
            }
            if (!parsed.protocol.equals("https", ignoreCase = true)) {
                return HttpResult.Failed(if (hop == 0) "sources must use https" else "that source redirected to an insecure link")
            }
            val connection = try {
                open(parsed)
            } catch (e: IOException) {
                return HttpResult.Failed("couldn't reach the source")
            }
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept-Encoding", "gzip")
                etag?.let { connection.setRequestProperty("If-None-Match", it) }
                val status = try {
                    connection.responseCode
                } catch (e: IOException) {
                    return HttpResult.Failed("couldn't reach the source")
                }
                when {
                    status == HttpURLConnection.HTTP_NOT_MODIFIED -> return HttpResult.NotModified
                    status in 300..399 -> {
                        target = connection.getHeaderField("Location") ?: return HttpResult.Failed("that source sent a redirect with no address")
                        if (target.startsWith("/")) target = URL(parsed, target).toString()
                        return@repeat
                    }
                    status != HttpURLConnection.HTTP_OK -> return HttpResult.Failed("the source answered $status", status)
                }
                val declared = connection.getHeaderFieldLong("Content-Length", -1)
                if (declared > maxBytes) return HttpResult.TooLarge
                val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
                    java.util.zip.GZIPInputStream(connection.inputStream)
                } else {
                    connection.inputStream
                }
                val bytes = stream.use { it.readAtMost(maxBytes) } ?: return HttpResult.TooLarge
                return HttpResult.Body(bytes, connection.getHeaderField("ETag"))
            } catch (e: IOException) {
                return HttpResult.Failed("couldn't read from the source")
            } finally {
                connection.disconnect()
            }
        }
        return HttpResult.Failed("that source redirected too many times")
    }

    companion object {
        const val USER_AGENT = "Folio"

        /** Reads up to [maxBytes], or null when there's more, so a huge or endless response can't fill memory. */
        internal fun InputStream.readAtMost(maxBytes: Int): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = read(buffer)
                if (read < 0) return out.toByteArray()
                if (out.size() + read > maxBytes) return null
                out.write(buffer, 0, read)
            }
        }
    }
}
