package com.mivisor.tv.catalog.tv

import com.mivisor.tv.catalog.AceStreamLink
import com.mivisor.tv.catalog.CatalogBuilder
import com.mivisor.tv.catalog.ChannelGroup
import com.mivisor.tv.catalog.ChannelGrouping
import com.mivisor.tv.catalog.ChannelMatcher
import com.mivisor.tv.catalog.ChannelSource
import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.LogoSections
import com.mivisor.tv.model.Catalog
import com.mivisor.tv.model.Channel
import com.mivisor.tv.model.ChannelKind

/** Un proveedor ya leído: id = código normalizado (nunca se muestra), nombre visible y su catálogo. */
class ProviderCatalog(
    val id: String,
    val name: String,
    val catalog: Catalog,
    val usesWarp: Boolean = false,
    /** Enlaces que no son Ace Stream (magnet, Telegram, TVGram, webs…): se abren con otra app. */
    val links: List<Channel> = emptyList()
)

/** Enlace externo de un proveedor: se abre con la app que Android elija (no se reproduce en MiVisor TV). */
class TvLink(val id: String, val name: String, val url: String, val kind: ChannelKind, val providerName: String) {
    val kindLabel: String get() = when (kind) {
        ChannelKind.MAGNET -> "Magnet"
        ChannelKind.TELEGRAM -> if (url.startsWith("tvgram", ignoreCase = true)) "TVGram" else "Telegram"
        ChannelKind.HLS, ChannelKind.VIDEO, ChannelKind.PLAYLIST -> "Vídeo"
        ChannelKind.WEB -> "Web"
        else -> "Enlace"
    }
}

/** Una fuente reproducible de un canal: el Content ID es la identidad para la fiabilidad. */
class TvSource(
    val contentId: String,
    val providerId: String,
    val providerName: String,
    /** 0 desconocida, 1 SD, 2 HD, 3 FHD, 4 4K (para el desempate de la fiabilidad). */
    val quality: Int,
    val qualityLabel: String?,
    val variantName: String
)

/** Un canal tal como lo ve el usuario ("DAZN LaLiga"), con todas sus fuentes de todos los proveedores. */
class TvChannel(
    /** Estable entre lecturas del proveedor: la clave canónica del nombre. */
    val id: String,
    val name: String,
    val brandKey: String?,
    val brandName: String?,
    val sources: List<TvSource>,
    val initials: String,
    /** Logo real (3c): el del canal o, si no tiene, el de su grupo. null = se pinta el rótulo de texto. */
    val logo: String? = null,
    /** Tipo (deportes, cine…) según el catálogo de logos o el nombre. */
    val category: com.mivisor.tv.catalog.logos.ChannelCategory? = null
) {
    /** Familia del diseño 06 (Fútbol, Motor, Deportes…). */
    val family: ChannelFamily by lazy { ChannelFamilies.of(name, category) }

    val multiaudio: Boolean get() = sources.any { MULTI.containsMatchIn(it.variantName) }
    val bestQuality: Int get() = sources.maxOfOrNull { it.quality } ?: 0

    private companion object { val MULTI = Regex("(?i)multi\\s*-?\\s*audio|dual") }
}

/** Una sección de Canales: una marca (DAZN, Movistar Plus+…), un tipo (Deportes…) u «Otros canales». */
class TvSection(val key: String, val name: String, val channels: List<TvChannel>, val logo: String? = null) {
    /** Marca conocida (Grupos) o temática / otros (Por temática). */
    val isBrand: Boolean get() = !key.startsWith("cat:") && key != "otros"
}

/** Todo lo que la interfaz necesita de los proveedores, calculado una vez fuera del hilo principal. */
class TvLibrary(
    val channels: List<TvChannel>,
    val sections: List<TvSection>,
    val providerCount: Int,
    val links: List<TvLink> = emptyList()
) {
    private val byId = channels.associateBy { it.id }
    fun channel(id: String): TvChannel? = byId[id]
    val isEmpty: Boolean get() = channels.isEmpty()

    val brandSections: List<TvSection> get() = sections.filter { it.isBrand }
    val themeSections: List<TvSection> get() = sections.filter { !it.isBrand }
    fun section(key: String): TvSection? = sections.firstOrNull { it.key == key }

    /**
     * «Proveedores» del diseño 06: las marcas reconocidas (DAZN, Movistar Plus+…) y, al final, «Otros / sin identificar»
     * con todo lo que no es de ninguna marca. Nunca se atribuye un canal a una marca por una coincidencia dudosa:
     * eso lo decide el catálogo de logos (BuiltinGroups + channel-logos.json).
     */
    val providers: List<TvSection> by lazy {
        val brands = brandSections
        val others = channels.filter { c -> brands.none { s -> s.channels.any { it.id == c.id } } }
        brands + if (others.isNotEmpty()) listOf(TvSection(OTHERS_KEY, "Otros / sin identificar", others)) else emptyList()
    }

    /** Sección en la que aparece el canal (para cambiar de canal con ▲▼ dentro de su grupo). */
    fun sectionOf(channelId: String): TvSection? = sections.firstOrNull { s -> s.channels.any { it.id == channelId } }

    /** Canal siguiente (+1) o anterior (−1) dentro de su sección, dando la vuelta al llegar al final. */
    fun neighbor(channelId: String, step: Int): TvChannel? {
        val list = sectionOf(channelId)?.channels ?: channels
        val i = list.indexOfFirst { it.id == channelId }
        if (i < 0 || list.size < 2) return null
        return list[((i + step) % list.size + list.size) % list.size]   // sin Math.floorMod (API 24)
    }

    private val matcher by lazy {
        ChannelMatcher(channels.map { Channel(it.id, it.name, it.id, null, ChannelKind.ACE, it.initials, 0) })
    }

    /** Tus canales para un nombre de canal de la agenda («DAZN LaLiga», «M+ Liga de Campeones»…), mejores primero. */
    fun forAgenda(agendaName: String, limit: Int = 3): List<TvChannel> =
        if (channels.isEmpty()) emptyList() else matcher.match(agendaName, limit).mapNotNull { byId[it.id] }

    companion object {
        val EMPTY = TvLibrary(emptyList(), emptyList(), 0)
        const val OTHERS_KEY = "sin-marca"

        /** Familias que tienen canales en [list], en el orden del diseño. */
        fun familiesOf(list: List<TvChannel>): List<Pair<ChannelFamily, Int>> {
            val counts = list.groupingBy { it.family }.eachCount()
            return ChannelFamily.values().mapNotNull { f -> counts[f]?.let { f to it } }
        }

        /** Canales en orden natural («DAZN 2» antes que «DAZN 10»). */
        fun sorted(list: List<TvChannel>): List<TvChannel> = list.sortedWith(compareBy(NaturalOrder) { it.name })

        fun build(providers: List<ProviderCatalog>, resolver: ChannelLogoResolver = ChannelLogoResolver(LogoCatalog.EMPTY)): TvLibrary {
            if (providers.isEmpty()) return EMPTY
            val groups: List<ChannelGroup> = ChannelGrouping.group(providers.map { p ->
                Triple(p.id, p.name, p.catalog.sections.flatMap { it.channels }.map { it.withProvider(p.id, p.name) })
            })
            // 3e.1: cada número es un CANAL distinto, no otra fuente: «M+ Liga de Campeones» y «M+ Liga de Campeones 2»
            // son dos canales (que la pantalla junta luego en el grupo «M+ Liga de Campeones»). Las fuentes de un
            // canal son solo las del mismo nombre con el mismo número, vengan de un proveedor o de varios.
            val channels = groups.flatMap { g -> g.variants }.mapNotNull { v ->
                val sources = v.sources.mapNotNull { s -> toSource(s) }.distinctBy { it.contentId }
                if (sources.isEmpty()) return@mapNotNull null
                val logo = resolver.resolve(v.name)
                TvChannel(v.key, v.name, logo.groupKey, logo.groupName, sources, CatalogBuilder.initialsOf(v.name), logo.best, logo.category)
            }.distinctBy { it.id }
            val sections = LogoSections.build(channels, resolver, name = { it.name }, listGroup = { null }).map { sec ->
                TvSection(sec.key ?: "otros", sec.name, sec.items.map { it.first }, sec.logo.groupLogo)
            }
            val links = providers.flatMap { p ->
                p.links.map { TvLink("${p.id}/${it.id}", it.name, it.url, it.kind, p.name) }
            }.distinctBy { it.url }
            return TvLibrary(channels, sections, providers.size, links)
        }

        private fun toSource(s: ChannelSource): TvSource? {
            val id = AceStreamLink.contentId(s.channel.url)?.lowercase() ?: return null
            return TvSource(id, s.providerId, s.providerName, s.quality?.rank ?: 0, s.quality?.label, s.variantName)
        }
    }
}
