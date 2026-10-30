package com.mivisor.tv.catalog.tv

import com.mivisor.tv.catalog.AceStreamLink
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
class ProviderCatalog(val id: String, val name: String, val catalog: Catalog, val usesWarp: Boolean = false)

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
    val initials: String
) {
    val multiaudio: Boolean get() = sources.any { MULTI.containsMatchIn(it.variantName) }
    val bestQuality: Int get() = sources.maxOfOrNull { it.quality } ?: 0

    private companion object { val MULTI = Regex("(?i)multi\\s*-?\\s*audio|dual") }
}

/** Una sección de Canales: una marca (DAZN, Movistar Plus+…), un tipo (Deportes…) u «Otros canales». */
class TvSection(val key: String, val name: String, val channels: List<TvChannel>)

/** Todo lo que la interfaz necesita de los proveedores, calculado una vez fuera del hilo principal. */
class TvLibrary(val channels: List<TvChannel>, val sections: List<TvSection>, val providerCount: Int) {
    private val byId = channels.associateBy { it.id }
    fun channel(id: String): TvChannel? = byId[id]
    val isEmpty: Boolean get() = channels.isEmpty()

    private val matcher by lazy {
        ChannelMatcher(channels.map { Channel(it.id, it.name, it.id, null, ChannelKind.ACE, it.initials, 0) })
    }

    /** Tus canales para un nombre de canal de la agenda («DAZN LaLiga», «M+ Liga de Campeones»…), mejores primero. */
    fun forAgenda(agendaName: String, limit: Int = 3): List<TvChannel> =
        if (channels.isEmpty()) emptyList() else matcher.match(agendaName, limit).mapNotNull { byId[it.id] }

    companion object {
        val EMPTY = TvLibrary(emptyList(), emptyList(), 0)

        fun build(providers: List<ProviderCatalog>, logos: LogoCatalog = LogoCatalog.EMPTY): TvLibrary {
            if (providers.isEmpty()) return EMPTY
            val groups: List<ChannelGroup> = ChannelGrouping.group(providers.map { p ->
                Triple(p.id, p.name, p.catalog.sections.flatMap { it.channels }.map { it.withProvider(p.id, p.name) })
            })
            val resolver = ChannelLogoResolver(logos)
            val channels = groups.mapNotNull { g ->
                val sources = g.sources.mapNotNull { s -> toSource(s) }.distinctBy { it.contentId }
                if (sources.isEmpty()) return@mapNotNull null
                val logo = resolver.resolve(g.name)
                TvChannel(g.key, g.name, logo.groupKey, logo.groupName, sources, g.initials)
            }
            val sections = LogoSections.build(channels, resolver, name = { it.name }, listGroup = { null }).map { sec ->
                TvSection(sec.key ?: "otros", sec.name, sec.items.map { it.first })
            }
            return TvLibrary(channels, sections, providers.size)
        }

        private fun toSource(s: ChannelSource): TvSource? {
            val id = AceStreamLink.contentId(s.channel.url)?.lowercase() ?: return null
            return TvSource(id, s.providerId, s.providerName, s.quality?.rank ?: 0, s.quality?.label, s.variantName)
        }
    }
}
