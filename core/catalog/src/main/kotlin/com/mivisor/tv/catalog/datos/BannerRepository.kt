package com.mivisor.tv.catalog.datos

import java.io.File

/**
 * 3j · Banners en la memoria de la app (filesDir/banners), como la agenda:
 *  - el manifest se consulta como mucho cada [manifestTtlMs] (con ETag);
 *  - cada imagen se descarga UNA vez. Solo se vuelve a pedir si el manifest dice que ha cambiado (su versión) o, si el
 *    manifest no trae versiones, cuando cambia el manifest (y entonces con If-None-Match: igual → 304, no baja nada);
 *  - lo que no existe (404) no se reintenta hasta el siguiente cambio del manifest;
 *  - «Borrar banners» lo vacía todo: en el siguiente refresco se vuelve a descargar.
 */
class BannerRepository(
    private val dir: File,
    private val fetcher: Fetcher = HttpFetch,
    private val clock: () -> Long = System::currentTimeMillis,
    manifestTtlMs: Long = 6 * 3600_000L
) {
    private class Meta(var etag: String? = null, var version: String? = null, var manifest: String? = null, var missing: Boolean = false, var at: Long = 0)

    private val manifest = CachedJson(dir, "manifest.json", manifestTtlMs, fetcher, clock)
    private val metaFile get() = File(dir, "index.tsv")
    private var metas: MutableMap<String, Meta>? = null

    @Volatile var catalog: BannerCatalog? = null
        private set
    /** Versión del manifest con la que se trabaja (cambia → hay que revalidar lo que no trae versión). */
    @Volatile private var manifestVersion: String = ""

    /** Lo guardado, sin red. */
    @Synchronized
    fun loadCached(): BannerCatalog? {
        catalog?.let { return it }
        val text = manifest.cached() ?: return null
        return runCatching { BannerCatalog.parse(text) }.getOrNull()?.also { catalog = it }
    }

    /** Consulta el manifest (si toca). Devuelve el catálogo o el guardado si no hay red. */
    fun refresh(manifestUrl: String, force: Boolean = false): BannerCatalog {
        val r = manifest.load(manifestUrl, force) { BannerCatalog.parse(it) }
        val c = BannerCatalog.parse(r.text)
        synchronized(this) { catalog = c; manifestVersion = r.version }
        return c
    }

    @Synchronized
    private fun metas(): MutableMap<String, Meta> = metas ?: runCatching {
        // Una línea por imagen: ruta \t etag \t versión \t manifest \t perdida(0/1) \t fecha (sin reflexión: sobrevive a R8).
        metaFile.readLines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 6) null
            else f[0] to Meta(f[1].ifEmpty { null }, f[2].ifEmpty { null }, f[3].ifEmpty { null }, f[4] == "1", f[5].toLongOrNull() ?: 0)
        }.toMap(HashMap())
    }.getOrDefault(HashMap()).also { metas = it }

    @Synchronized
    private fun saveMetas() {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "index.tsv.tmp")
            fun c(v: String?) = v.orEmpty().replace('\t', ' ').replace('\n', ' ')
            tmp.writeText(metas().entries.joinToString("\n") { (k, m) ->
                listOf(c(k), c(m.etag), c(m.version), c(m.manifest), if (m.missing) "1" else "0", m.at.toString()).joinToString("\t")
            })
            if (!tmp.renameTo(metaFile)) { metaFile.delete(); tmp.renameTo(metaFile) }
        }
    }

    fun fileOf(e: BannerCatalog.Entry): File = File(File(dir, "img"), e.path.substringAfter("://").replace(Regex("""[^A-Za-z0-9._-]"""), "_"))

    /** El fichero ya guardado (sin red), si lo hay. */
    fun cachedFile(e: BannerCatalog.Entry): File? = fileOf(e).takeIf { it.isFile && it.length() > 0 }

    /** ¿Hace falta preguntar a GitHub por esta imagen? */
    @Synchronized
    fun needsCheck(e: BannerCatalog.Entry): Boolean {
        val m = metas()[e.path]
        val have = cachedFile(e) != null
        return when {
            m == null -> true
            m.missing -> m.manifest != manifestVersion
            !have -> true
            e.version != null -> m.version != e.version
            else -> m.manifest != manifestVersion && manifestVersion.isNotEmpty()
        }
    }

    /**
     * El fichero de [e], descargándolo solo si hace falta. null = no existe en GitHub o no hay red y no estaba guardado.
     */
    fun ensure(e: BannerCatalog.Entry, baseUrl: String): File? {
        val file = fileOf(e)
        if (!needsCheck(e)) return cachedFile(e)
        val url = if (e.path.startsWith("https://")) e.path else baseUrl.trimEnd('/') + "/" + e.path
        val m = synchronized(this) { metas()[e.path] }
        val r = try {
            fetcher.get(url, m?.etag?.takeIf { file.isFile }, MAX_BYTES)
        } catch (ex: Exception) {
            return cachedFile(e)                          // sin red: lo que haya
        }
        val now = clock()
        when {
            r.status == 304 -> synchronized(this) { metas()[e.path] = Meta(m?.etag, e.version, manifestVersion, false, now) }
            r.status == 200 && r.body != null -> {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(r.body)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                synchronized(this) { metas()[e.path] = Meta(r.etag, e.version, manifestVersion, false, now) }
            }
            r.status == 404 -> {
                file.delete()
                synchronized(this) { metas()[e.path] = Meta(null, e.version, manifestVersion, true, now) }
            }
            else -> return cachedFile(e)
        }
        saveMetas()
        return cachedFile(e)
    }

    /** Espacio usado (bytes). */
    fun usedBytes(): Long = File(dir, "img").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** «Borrar banners»: imágenes, índice y manifest. */
    @Synchronized
    fun clear() {
        runCatching { dir.deleteRecursively() }
        metas = mutableMapOf()
        catalog = null
        manifestVersion = ""
    }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
