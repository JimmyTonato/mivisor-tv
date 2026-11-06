package com.mivisor.tv.catalog.tv

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.mivisor.tv.catalog.logos.ChannelCategory
import com.mivisor.tv.model.ChannelKind
import java.io.File

/**
 * 3i · La biblioteca de canales YA MONTADA, guardada en disco (filesDir/library-snapshot.json). Al abrir la app se lee
 * y se enseña al momento, sin leer los proveedores ni agrupar nada; después se leen los proveedores como siempre y, si
 * lo leído tiene la misma huella ([TvLibrary.fingerprint]) que la guardada, no se vuelve a montar.
 *
 * Formato propio y compacto (lectura/escritura en flujo, sin reflexión): versión, huella y los datos tal cual.
 * Si el archivo falta, está a medias o es de otra versión, se ignora (y se monta como siempre).
 */
object LibrarySnapshot {
    const val VERSION = 1

    class Loaded(val library: TvLibrary, val fingerprint: Long)

    fun write(file: File, lib: TvLibrary, fingerprint: Long) {
        val tmp = File(file.path + ".tmp")
        file.parentFile?.mkdirs()
        JsonWriter(tmp.bufferedWriter()).use { w ->
            w.beginObject()
            w.name("v").value(VERSION.toLong())
            w.name("print").value(fingerprint)
            w.name("providers").value(lib.providerCount.toLong())
            w.name("channels").beginArray()
            for (c in lib.channels) {
                w.beginObject()
                w.name("id").value(c.id)
                w.name("n").value(c.name)
                c.brandKey?.let { w.name("bk").value(it) }
                c.brandName?.let { w.name("bn").value(it) }
                w.name("i").value(c.initials)
                c.logo?.let { w.name("l").value(it) }
                c.category?.let { w.name("c").value(it.name) }
                w.name("s").beginArray()
                for (s in c.sources) {
                    w.beginArray()
                    w.value(s.contentId).value(s.providerId).value(s.providerName).value(s.quality.toLong())
                    if (s.qualityLabel == null) w.nullValue() else w.value(s.qualityLabel)
                    w.value(s.variantName)
                    w.endArray()
                }
                w.endArray()
                w.endObject()
            }
            w.endArray()
            w.name("sections").beginArray()
            for (s in lib.sections) {
                w.beginObject()
                w.name("k").value(s.key)
                w.name("n").value(s.name)
                s.logo?.let { w.name("l").value(it) }
                w.name("ids").beginArray()
                s.channels.forEach { w.value(it.id) }
                w.endArray()
                w.endObject()
            }
            w.endArray()
            w.name("links").beginArray()
            for (l in lib.links) {
                w.beginArray()
                w.value(l.id).value(l.name).value(l.url).value(l.kind.name).value(l.providerName)
                w.endArray()
            }
            w.endArray()
            w.endObject()
        }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun read(file: File): Loaded? = runCatching {
        if (!file.isFile) return null
        JsonReader(file.bufferedReader()).use { r -> readFrom(r) }
    }.getOrNull()

    private fun readFrom(r: JsonReader): Loaded? {
        var version = -1
        var print = 0L
        var providers = 0
        val channels = ArrayList<TvChannel>()
        val sectionsRaw = ArrayList<Triple<Pair<String, String>, String?, List<String>>>()
        val links = ArrayList<TvLink>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "v" -> version = r.nextInt()
                "print" -> print = r.nextLong()
                "providers" -> providers = r.nextInt()
                "channels" -> {
                    if (version != VERSION) return null
                    r.beginArray()
                    while (r.hasNext()) channels += readChannel(r)
                    r.endArray()
                }
                "sections" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        var k = ""; var n = ""; var l: String? = null; val ids = ArrayList<String>()
                        r.beginObject()
                        while (r.hasNext()) when (r.nextName()) {
                            "k" -> k = r.nextString()
                            "n" -> n = r.nextString()
                            "l" -> l = r.nextString()
                            "ids" -> { r.beginArray(); while (r.hasNext()) ids += r.nextString(); r.endArray() }
                            else -> r.skipValue()
                        }
                        r.endObject()
                        sectionsRaw += Triple(k to n, l, ids)
                    }
                    r.endArray()
                }
                "links" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        r.beginArray()
                        val id = r.nextString(); val n = r.nextString(); val u = r.nextString(); val k = r.nextString(); val p = r.nextString()
                        r.endArray()
                        val kind = runCatching { ChannelKind.valueOf(k) }.getOrNull() ?: continue
                        links += TvLink(id, n, u, kind, p)
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (version != VERSION || channels.isEmpty()) return null
        val byId = channels.associateBy { it.id }
        val sections = sectionsRaw.map { (kn, l, ids) -> TvSection(kn.first, kn.second, ids.mapNotNull { byId[it] }, l) }
        return Loaded(TvLibrary(channels, sections, providers, links), print)
    }

    private fun readChannel(r: JsonReader): TvChannel {
        var id = ""; var n = ""; var bk: String? = null; var bn: String? = null; var i = ""; var l: String? = null
        var c: ChannelCategory? = null
        val sources = ArrayList<TvSource>()
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "id" -> id = r.nextString()
            "n" -> n = r.nextString()
            "bk" -> bk = r.nextString()
            "bn" -> bn = r.nextString()
            "i" -> i = r.nextString()
            "l" -> l = r.nextString()
            "c" -> c = runCatching { ChannelCategory.valueOf(r.nextString()) }.getOrNull()
            "s" -> {
                r.beginArray()
                while (r.hasNext()) {
                    r.beginArray()
                    val cid = r.nextString(); val pid = r.nextString(); val pn = r.nextString(); val q = r.nextInt()
                    val ql = if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextString()
                    val vn = r.nextString()
                    r.endArray()
                    sources += TvSource(cid, pid, pn, q, ql, vn)
                }
                r.endArray()
            }
            else -> r.skipValue()
        }
        r.endObject()
        return TvChannel(id, n, bk, bn, sources, i, l, c)
    }
}
