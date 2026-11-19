package com.n3k0chan.panelcasa.net

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP mínimo sobre HttpURLConnection, sin dependencias.
 * En Android respeta network_security_config; en la JVM usa el almacén de confianza del sistema.
 */
interface Fetcher {
    @Throws(IOException::class)
    fun get(url: String, etag: String? = null, lastModified: String? = null, maxBytes: Int = 4 * 1024 * 1024): Response

    /** [onProgress] recibe bytes descargados y total (-1 si el servidor no lo indica). */
    @Throws(IOException::class)
    fun download(
        url: String,
        dest: File,
        etag: String? = null,
        lastModified: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): Response

    class Response(
        val code: Int,
        val body: ByteArray?,
        val etag: String?,
        val lastModified: String?,
        val expires: String?,
    ) {
        val notModified: Boolean get() = code == HttpURLConnection.HTTP_NOT_MODIFIED
        val ok: Boolean get() = code in 200..299
    }
}

class HttpFetcher(
    private val userAgent: String,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : Fetcher {

    /** GET en memoria. Pensado para respuestas pequeñas (tiempo real, meteo). */
    override fun get(url: String, etag: String?, lastModified: String?, maxBytes: Int): Fetcher.Response {
        val conn = open(url, etag, lastModified)
        try {
            val code = conn.responseCode
            val body = if (code in 200..299) conn.inputStream.use { readLimited(it, maxBytes) } else null
            return Fetcher.Response(code, body, conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"), conn.getHeaderField("Expires"))
        } finally {
            conn.disconnect()
        }
    }

    /**
     * GET a fichero. Descarga a un temporal y solo sustituye [dest] si la descarga termina bien,
     * para no perder nunca el último fichero válido.
     */
    override fun download(
        url: String,
        dest: File,
        etag: String?,
        lastModified: String?,
        onProgress: ((Long, Long) -> Unit)?,
    ): Fetcher.Response {
        val conn = open(url, etag, lastModified)
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                val tmp = File(dest.parentFile, dest.name + ".part")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var reported = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            // Avisos cada 256 KB como mucho, para no saturar la interfaz.
                            if (onProgress != null && done - reported >= 256 * 1024) {
                                reported = done
                                onProgress(done, total)
                            }
                        }
                        onProgress?.invoke(done, total)
                    }
                }
                if (dest.exists() && !dest.delete()) throw IOException("No se puede sustituir $dest")
                if (!tmp.renameTo(dest)) throw IOException("No se puede renombrar $tmp")
            }
            return Fetcher.Response(code, null, conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"), conn.getHeaderField("Expires"))
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, etag: String?, lastModified: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", userAgent)
        if (etag != null) conn.setRequestProperty("If-None-Match", etag)
        if (lastModified != null) conn.setRequestProperty("If-Modified-Since", lastModified)
        return conn
    }

    private fun readLimited(raw: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = raw.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("Respuesta mayor de $maxBytes bytes")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
