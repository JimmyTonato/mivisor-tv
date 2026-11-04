package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.tv.ChannelSeriesGrouping
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.Catalog
import com.mivisor.tv.model.Channel
import com.mivisor.tv.model.ChannelKind
import com.mivisor.tv.model.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3e.1: «M+ Liga de Campeones 2» es OTRO canal, no otra fuente de «M+ Liga de Campeones». */
class NumberedChannelsTest {
    private fun ch(id: Int, name: String) = Channel("c$id", name, "acestream://" + "%040x".format(id), null, ChannelKind.ACE, "", 0)

    @Test fun cadaNumeroEsUnCanal() {
        val list = listOf(
            ch(1, "Movistar Liga de Campeones FHD"), ch(2, "M+ Liga de Campeones 1080"), ch(3, "Movistar Liga de Campeones 2"),
            ch(4, "DAZN 1"), ch(5, "DAZN 2 HD"), ch(6, "DAZN 3"),
            ch(7, "M+ LaLiga"), ch(8, "M+ LaLiga 2"), ch(9, "M+ LaLiga 3"), ch(10, "Movistar LaLiga 2 FHD")
        )
        val lib = TvLibrary.build(listOf(ProviderCatalog("p", "Prov", Catalog(listOf(Section("s", "S", list)), list.size))))
        val ids = lib.channels.map { it.id }
        assertEquals(ids.toString(), 8, lib.channels.size)
        assertEquals(2, lib.channels.first { it.id == "movistar champions" }.sources.size)
        assertEquals(1, lib.channels.first { it.id == "movistar champions 2" }.sources.size)
        assertEquals(2, lib.channels.first { it.id == "movistar laliga 2" }.sources.size)
        assertEquals(listOf("dazn 1", "dazn 2", "dazn 3"), ids.filter { it.startsWith("dazn") })
        assertTrue(lib.channels.map { it.name }.toString(), lib.channels.any { it.name.endsWith(" 2") && it.name.contains("Campeones") })
        // En pantalla se juntan en grupos: Liga de Campeones (sin número y 2), DAZN (1, 2, 3), LaLiga (sin número, 2, 3).
        val series = ChannelSeriesGrouping.group(lib.channels).filter { it.channels.size > 1 }.map { it.channels.size }.sorted()
        assertEquals(listOf(2, 3, 3), series)
    }
}
