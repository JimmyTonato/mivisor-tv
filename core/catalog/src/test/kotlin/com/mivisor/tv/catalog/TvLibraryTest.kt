package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.tv.ChannelPlates
import com.mivisor.tv.catalog.tv.Plate
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.catalog.tv.TvSearch
import com.mivisor.tv.model.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLibraryTest {
    private fun id(n: Int) = n.toString(16).padStart(40, '0')

    private fun provider(id: String, vararg items: Pair<String, Int>) = ProviderCatalog(
        id, "Proveedor $id",
        CatalogBuilder.build(items.map { StreamItem(name = it.first, url = "acestream://" + id(it.second)) })
    )

    @Test fun agrupaCanalesDeVariosProveedoresYMarcas() {
        val lib = TvLibrary.build(listOf(
            provider("a", "DAZN LaLiga FHD" to 1, "M+ LaLiga" to 2, "DAZN 1" to 3),
            provider("b", "ES: DAZN LALIGA 1080p" to 4, "DAZN LaLiga" to 1)
        ))
        val laliga = lib.channels.first { it.name.lowercase().contains("dazn laliga") }
        assertEquals("mismo Content ID = una sola fuente", 2, laliga.sources.size)
        assertEquals("dazn", laliga.brandKey)
        assertTrue(lib.sections.any { it.key == "dazn" && it.channels.size >= 2 })
        assertTrue(lib.sections.any { it.key == "movistar" })
    }

    @Test fun losIdsDeCanalSonEstablesEntreLecturas() {
        val a = TvLibrary.build(listOf(provider("a", "DAZN LaLiga" to 1)))
        val b = TvLibrary.build(listOf(provider("a", "DAZN LaLiga HD" to 9, "DAZN LaLiga" to 1)))
        assertEquals(a.channels.first().id, b.channels.first { it.name.contains("LaLiga") }.id)
    }

    @Test fun agendaEncuentraTusCanales() {
        val lib = TvLibrary.build(listOf(provider("a", "DAZN LaLiga FHD" to 1, "M+ LaLiga" to 2, "Eurosport 1" to 3)))
        val found = lib.forAgenda("DAZN LaLiga")
        assertTrue(found.isNotEmpty())
        assertTrue(found.first().name.contains("DAZN", ignoreCase = true))
        assertTrue(lib.forAgenda("Canal inexistente XYZ").isEmpty())
    }

    @Test fun zappingDentroDeLaSeccionYEnlacesExternos() {
        val lib = TvLibrary.build(listOf(ProviderCatalog("a", "A",
            CatalogBuilder.build(listOf(StreamItem(name = "DAZN LaLiga", url = "acestream://" + id(1)), StreamItem(name = "DAZN F1", url = "acestream://" + id(2)))),
            links = CatalogBuilder.build(listOf(StreamItem(name = "Canal TG", url = "tg://resolve?domain=x")), aceOnly = false).sections.flatMap { it.channels }
        )))
        val d1 = lib.channels.first { it.name == "DAZN LaLiga" }
        val d2 = lib.neighbor(d1.id, 1)!!
        assertEquals("DAZN F1", d2.name)
        assertEquals(d1.id, lib.neighbor(d1.id, 1 + 1)!!.id)
        assertEquals(d2.id, lib.neighbor(d1.id, -1)!!.id)
        assertEquals(1, lib.links.size)
        assertEquals("Telegram", lib.links.first().kindLabel)
        assertTrue(lib.brandSections.any { it.key == "dazn" })
    }

    @Test fun busquedaToleraErratas() {
        assertTrue(TvSearch.score("betys", "Real Betis Balompié") > 0)
        assertTrue(TvSearch.score("dazn laliga", "DAZN LaLiga") > 0)
        assertEquals(0, TvSearch.score("xqz", "DAZN LaLiga"))
        assertEquals(1, TvSearch.distance("betys", "betis"))
    }
}

class ChannelPlatesTest {
    @Test fun rotulosComoEnElPrototipo() {
        assertEquals(Plate("DAZN", "LALIGA", Plate.Tone.LIGHT), ChannelPlates.of("DAZN LaLiga", "dazn"))
        assertEquals(Plate("M+", "DEPORTES", Plate.Tone.DARK), ChannelPlates.of("M+ Deportes", "movistar"))
        assertEquals(Plate("EURO", "SPORT 1", Plate.Tone.BLUE), ChannelPlates.of("Eurosport 1", "eurosport"))
        assertEquals(Plate("DAZN", null, Plate.Tone.LIGHT), ChannelPlates.of("DAZN", "dazn"))
        assertEquals(Plate("LA 1", null, Plate.Tone.DARK), ChannelPlates.of("La 1", null))
        assertEquals(Plate("ANTENA", "3", Plate.Tone.DARK), ChannelPlates.of("Antena 3", null))
        assertEquals("BET", ChannelPlates.crest("Real Betis"))
        assertEquals("SEV", ChannelPlates.crest("Sevilla FC"))
    }
}
