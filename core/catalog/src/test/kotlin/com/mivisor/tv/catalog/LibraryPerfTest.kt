package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.Picons
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.Catalog
import com.mivisor.tv.model.Channel
import com.mivisor.tv.model.ChannelKind
import com.mivisor.tv.model.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 3g · Montar la biblioteca una sola vez y cambiar logos sin reagrupar. */
class LibraryPerfTest {
    private fun ch(id: Int, name: String) = Channel("c$id", name, "acestream://" + "%040x".format(id), null, ChannelKind.ACE, "", 0)
    private fun prov(vararg names: String) = listOf(ProviderCatalog("p", "Prov", Catalog(listOf(Section("s", "S", names.mapIndexed { i, n -> ch(i + 1, n) })), names.size)))

    @Test fun huellaIgualSiNoCambiaNada() {
        assertEquals(TvLibrary.fingerprint(prov("DAZN 1", "M+ LaLiga")), TvLibrary.fingerprint(prov("DAZN 1", "M+ LaLiga")))
        assertNotEquals(TvLibrary.fingerprint(prov("DAZN 1", "M+ LaLiga")), TvLibrary.fingerprint(prov("DAZN 1", "M+ LaLiga 2")))
    }

    @Test fun relogoCambiaLogosSinTocarCanales() {
        val lib = TvLibrary.build(prov("Movistar LaLiga 2 FHD", "DAZN 1"))
        assertNull(lib.channel("movistar laliga 2")?.logo)
        val r = ChannelLogoResolver(LogoCatalog.EMPTY, Picons.entries(listOf("M+ LaLiga 2.png")))
        val after = TvLibrary.relogo(lib, r)
        assertEquals(lib.channels.map { it.id }, after.channels.map { it.id })
        assertEquals(lib.channels.map { it.sources.size }, after.channels.map { it.sources.size })
        assertEquals(Picons.url("M+ LaLiga 2.png"), after.channel("movistar laliga 2")?.logo)
    }

    @Test fun emparejarAgendaConIndiceDaLoMismo() {
        val list = listOf(ch(1, "DAZN LaLiga"), ch(2, "DAZN LaLiga 2"), ch(3, "LaLiga DAZN Multi"), ch(4, "M+ LaLiga"), ch(5, "DAZN"))
        val m = ChannelMatcher(list)
        assertEquals(listOf("DAZN LaLiga", "LaLiga DAZN Multi"), m.match("DAZN LaLiga").map { it.name })
        assertEquals(listOf("DAZN LaLiga 2"), m.match("DAZN LaLiga 2").map { it.name })
        assertEquals(emptyList<String>(), m.match("Gol TV").map { it.name })
        assertEquals(m.match("DAZN LaLiga"), m.match("DAZN LaLiga"))   // segunda vez, de memoria
    }
}
