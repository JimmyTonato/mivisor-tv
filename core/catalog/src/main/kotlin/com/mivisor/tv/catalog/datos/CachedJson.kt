package com.mivisor.tv.catalog.datos

import java.io.File

/**
 * 3j · Un JSON de GitHub con copia local, igual que la agenda:
 *  - con menos de [ttlMs] se usa la copia sin red;
 *  - con ETag (304 = no ha cambiado, no se descarga);
 *  - solo se guarda si [validate] lo acepta (nunca se pisa la última copia buena con una rota);
 *  - sin red, la última copia buena.
 */
class CachedJson(
    private val dir: File,
    private val name: String,
    private val ttlMs: Long,
    private val fetcher: Fetcher = HttpFetch,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxBytes: Int = 2 * 1024 * 1024
) {
    class Result(
        val text: String,
        /** Ha llegado una versión distinta de la guardada (ETag/contenido nuevo). */
        val changed: Boolean,
        /** No se pudo consultar GitHub: es la copia guardada. */
        val offline: Boolean,
        val fetchedAt: Long,
        /** Versión (ETag o huella del contenido) de lo que hay guardado. */
        val version: String
    )

    private val data get() = File(dir, name)
    private val meta get() = File(dir, "$name.meta")

    fun cached(): String? = runCatching { data.takeIf { it.isFile }?.readText() }.getOrNull()

    private fun readMeta(): Triple<Long, String?, String> {
        val l = runCatching { meta.readText().split('\n') }.getOrDefault(emptyList())
        return Triple(l.getOrNull(0)?.toLongOrNull() ?: 0L, l.getOrNull(1)?.takeIf { it.isNotBlank() }, l.getOrNull(2).orEmpty())
    }

    private fun writeMeta(at: Long, etag: String?, version: String) {
        runCatching { dir.mkdirs(); meta.writeText("$at\n${etag.orEmpty()}\n$version") }
    }

    /** Olvida la versión guardada (la próxima vez se vuelve a descargar entero). */
    fun forget() {
        runCatching { meta.delete(); data.delete() }
    }

    /** @throws Exception si no hay red ni copia, o si lo descargado no es válido y no hay copia. */
    fun load(url: String, force: Boolean = false, validate: (String) -> Unit): Result {
        val (at, etag, version) = readMeta()
        val copy = cached()
        val now = clock()
        if (!force && copy != null && now - at in 0..ttlMs) return Result(copy, false, false, at, version)
        try {
            val r = fetcher.get(url, if (copy != null) etag else null, maxBytes)
            return when {
                r.status == 304 && copy != null -> { writeMeta(now, etag, version); Result(copy, false, false, now, version) }
                r.status == 200 && r.body != null -> {
                    val text = String(r.body, Charsets.UTF_8)
                    validate(text)
                    val v = r.etag ?: ("h" + text.hashCode().toUInt().toString(16) + text.length)
                    val changed = copy != text
                    dir.mkdirs()
                    val tmp = File(dir, "$name.tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(data)) { data.delete(); tmp.renameTo(data) }
                    writeMeta(now, r.etag, v)
                    Result(text, changed, false, now, v)
                }
                else -> throw java.io.IOException("HTTP ${r.status}")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (copy != null) return Result(copy, false, true, at, version)
            throw e
        }
    }
}
