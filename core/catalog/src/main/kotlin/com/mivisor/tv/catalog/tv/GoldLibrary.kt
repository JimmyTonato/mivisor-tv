package com.mivisor.tv.catalog.tv

import com.mivisor.tv.catalog.CatalogBuilder
import com.mivisor.tv.catalog.biblioteca.Biblioteca
import com.mivisor.tv.catalog.biblioteca.Canal
import com.mivisor.tv.catalog.biblioteca.Categoria
import com.mivisor.tv.catalog.biblioteca.GoldId
import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoCatalog

/**
 * 4f · La Biblioteca de MiVisor Gold convertida en la [TvLibrary] que usa la interfaz.
 *
 *  - Canales por «orden» (ascendente) y, dentro de cada uno, los IDs tal como vienen (ids[0] es el mejor).
 *  - Secciones = categorías raíz (marcas) por su «orden»; cada canal va en la raíz de su categoría.
 *  - Al aplicar cambios, los canales que no cambiaron se REUTILIZAN (mismo objeto): la pantalla solo repinta los
 *    canales y categorías afectados.
 */
object GoldLibrary {
    const val PROVIDER_ID = "gold"
    const val PROVIDER_NAME = "Biblioteca"
    private const val OTHERS = "otros"

    fun build(
        bib: Biblioteca,
        resolver: ChannelLogoResolver = ChannelLogoResolver(LogoCatalog.EMPTY),
        previous: TvLibrary? = null,
        touchedChannels: Set<String>? = null,
        touchedCategories: Set<String> = emptySet()
    ): TvLibrary {
        if (bib.isEmpty) return TvLibrary(emptyList(), emptyList(), 0, gold = true)
        val cats = bib.categorias
        val rootCache = HashMap<String?, Categoria?>()
        fun root(id: String?): Categoria? = rootCache.getOrPut(id) {
            var c = id?.let { cats[it] } ?: return@getOrPut null
            val seen = HashSet<String>()
            while (c.parentId != null && seen.add(c.id)) c = cats[c.parentId] ?: break
            c
        }
        fun chain(id: String?): Set<String> {
            val out = HashSet<String>()
            var c = id?.let { cats[it] }
            while (c != null && out.add(c.id)) c = c.parentId?.let { cats[it] }
            return out
        }
        val prev = previous?.takeIf { it.gold }
        val ordered = bib.canales.values.withIndex().sortedWith(compareBy({ it.value.orden }, { it.index })).map { it.value }
        val channels = ordered.mapNotNull { ch ->
            if (ch.ids.isEmpty()) return@mapNotNull null
            val reuse = prev?.channel(ch.channelId)?.takeIf {
                touchedChannels != null && ch.channelId !in touchedChannels &&
                    chain(ch.categoryId).none { c -> c in touchedCategories } && it.orden == ch.orden
            }
            reuse ?: channel(ch, root(ch.categoryId), resolver)
        }
        val byRoot = LinkedHashMap<String, MutableList<TvChannel>>()
        for (c in channels) byRoot.getOrPut(c.brandKey ?: OTHERS) { ArrayList() } += c
        val roots = cats.values.filter { it.parentId == null || cats[it.parentId] == null }.sortedBy { it.orden }
        val sections = roots.mapNotNull { r ->
            byRoot[r.id]?.let { TvSection("gold:${r.id}", r.name, it, resolver.resolve(r.name).groupLogo) }
        } + listOfNotNull(byRoot[OTHERS]?.let { TvSection(OTHERS, "Otros canales", it) })
        return TvLibrary(channels, sections, 0, gold = true)
    }

    private fun channel(ch: Canal, root: Categoria?, resolver: ChannelLogoResolver): TvChannel {
        val logo = resolver.resolve(ch.name)
        val sources = ch.ids.map { source(it, ch.name) }
        return TvChannel(
            ch.channelId, ch.name, root?.id, root?.name, sources, CatalogBuilder.initialsOf(ch.name),
            logo.best, logo.category, ch.orden
        )
    }

    const val EXTRA_SECTION = "mis-proveedores"

    /** Caché de canales mezclados: si no cambian ni el canal de Gold ni sus fuentes extra, el mismo objeto. */
    class MergeCache {
        internal val map = HashMap<String, Triple<TvChannel, List<String>, TvChannel>>()
    }

    /**
     * 4i · La Biblioteca de Gold es la fuente principal, pero los proveedores se siguen pudiendo usar: sus IDs que NO
     * están en la Biblioteca se añaden.
     *  - Si el canal ya existe en la Biblioteca (mismo nombre), sus IDs nuevos van DETRÁS de los de Gold (que mantienen
     *    su orden fijo; los nuevos los ordena la fiabilidad de la app).
     *  - Si es un canal que Gold no tiene, aparece en la sección «De tus proveedores», detrás de los de Gold.
     * Los IDs que Gold ya tiene se ignoran del proveedor: la información buena es la de Gold.
     */
    fun merge(gold: TvLibrary, providers: TvLibrary, cache: MergeCache? = null): TvLibrary {
        if (providers.isEmpty) return gold
        val goldIds = HashSet<String>()
        gold.channels.forEach { c -> c.sources.forEach { goldIds += it.contentId } }
        val byKey = HashMap<String, TvChannel>()
        gold.channels.forEach { c -> byKey.putIfAbsent(nameKey(c.name), c) }
        val extras = LinkedHashMap<String, MutableList<TvSource>>()   // id del canal de Gold → fuentes nuevas
        val newChannels = ArrayList<TvChannel>()
        var next = (gold.channels.maxOfOrNull { it.orden ?: 0 } ?: 0) + 1
        for (pc in TvLibrary.sorted(providers.channels)) {
            val fresh = pc.sources.filter { it.contentId !in goldIds }
            if (fresh.isEmpty()) continue
            fresh.forEach { goldIds += it.contentId }
            val target = byKey[nameKey(pc.name)]
            if (target != null) extras.getOrPut(target.id) { ArrayList() } += fresh
            else {
                val id = if (gold.channel(pc.id) != null) "prov:" + pc.id else pc.id
                newChannels += TvChannel(id, pc.name, pc.brandKey, pc.brandName, fresh, pc.initials, pc.logo, pc.category, next++)
            }
        }
        if (extras.isEmpty() && newChannels.isEmpty()) return gold
        val replaced = HashMap<String, TvChannel>()
        for ((id, add) in extras) {
            val g = gold.channel(id) ?: continue
            val cids = add.map { it.contentId }
            val hit = cache?.map?.get(id)?.takeIf { it.first === g && it.second == cids }?.third
            val merged = hit ?: TvChannel(g.id, g.name, g.brandKey, g.brandName, g.sources + add, g.initials, g.logo, g.category, g.orden)
            cache?.map?.put(id, Triple(g, cids, merged))
            replaced[id] = merged
        }
        fun swap(c: TvChannel) = replaced[c.id] ?: c
        val channels = gold.channels.map(::swap) + newChannels
        val sections = gold.sections.map { s -> if (s.channels.none { it.id in replaced }) s else TvSection(s.key, s.name, s.channels.map(::swap), s.logo) } +
            listOfNotNull(newChannels.takeIf { it.isNotEmpty() }?.let { TvSection(EXTRA_SECTION, "De tus proveedores", it) })
        return TvLibrary(channels, sections, providers.providerCount, providers.links, gold = true)
    }

    /** Clave para decir que dos nombres son el mismo canal («M+ LaLiga» = «Movistar LaLiga»). */
    private fun nameKey(name: String): String =
        com.mivisor.tv.catalog.ChannelCanon.parse(name).variantKey.ifEmpty { name.trim().lowercase() }

    /** SHA-1 (hex) de los bytes del descriptor; null si el base64 no vale. */
    fun sha1Of(base64: String): String? {
        val bytes = com.mivisor.tv.catalog.Base64Lite.decode(base64) ?: return null
        return java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun source(id: GoldId, channelName: String): TvSource {
        val v = id.verificacion
        val q = v?.calidad
        return TvSource(
            id.assetId, PROVIDER_ID, PROVIDER_NAME, q?.rank ?: 0, q?.etiqueta ?: q?.clase, channelName,
            GoldSourceInfo(
                band = v?.banda, nota = v?.nota, onTrial = id.onTrial, firstPieceS = v?.primeraPiezaS, checked = v?.comprobado,
                descriptorB64 = v?.descriptor?.base64, descriptorSha1 = v?.descriptor?.base64?.let(::sha1Of), emision = v?.emision, avisos = v?.avisos.orEmpty(), info = v?.info.orEmpty(),
                audios = q?.audios.orEmpty().map { a -> listOfNotNull(a.codec, a.canales, a.idioma?.let { "($it)" }).joinToString(" ") }
                    .filter { it.isNotBlank() },
                declaredVsReal = q?.declaradaVsReal
            )
        )
    }
}
