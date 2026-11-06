package com.mivisor.tv.catalog.datos

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 3j · Una descarga: estado HTTP, cuerpo (solo con 200) y ETag. */
class Fetched(val status: Int, val body: ByteArray?, val etag: String?)

/** Descargador (inyectable en las pruebas). */
fun interface Fetcher {
    fun get(url: String, etag: String?, maxBytes: Int): Fetched
}

/** HTTPS con If-None-Match: si no ha cambiado, 304 y no se descarga nada. */
object HttpFetch : Fetcher {
    override fun get(url: String, etag: String?, maxBytes: Int): Fetched {
        if (!url.startsWith("https://")) throw IOException("Solo HTTPS")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "MiVisorTV/1")
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            val status = c.responseCode
            if (status != 200) return Fetched(status, null, null)
            val bytes = c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException("Fichero demasiado grande")
                }
                out.toByteArray()
            }
            return Fetched(200, bytes, c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }
}
