package com.mivisor.tv.catalog.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4i · Biblioteca de Gold como fuente principal + lo nuevo de los proveedores. */
class GoldMergeTest {
    private fun gs(id: String) = TvSource(id, GoldLibrary.PROVIDER_ID, GoldLibrary.PROVIDER_NAME, 2, "HD", "x",
        GoldSourceInfo("verde", 90, false, null, null, null, null, null, emptyList(), emptyList(), emptyList(), null))
    private fun ps(id: String) = TvSource(id, "p1", "Prov", 1, null, "x")
    private fun ch(id: String, name: String, src: List<TvSource>, orden: Int? = null) =
        TvChannel(id, name, null, null, src, "XX", orden = orden)

    private val gold = run {
        val a = ch("g1", "DAZN 1", listOf(gs("a1"), gs("a2")), 1)
        val b = ch("g2", "Movistar LaLiga", listOf(gs("b1")), 2)
        TvLibrary(listOf(a, b), listOf(TvSection("gold:dazn", "DAZN", listOf(a, b))), 0, gold = true)
    }

    @Test fun idsQueGoldYaTieneSeIgnoran() {
        val prov = TvLibrary(listOf(ch("p", "DAZN 1", listOf(ps("a1"), ps("a2")))), emptyList(), 1)
        val out = GoldLibrary.merge(gold, prov)
        assertEquals(listOf("a1", "a2"), out.channel("g1")!!.sources.map { it.contentId })
        assertEquals(2, out.channels.size)
        assertSame(gold.channel("g1"), out.channel("g1"))
    }

    @Test fun idNuevoDeUnCanalDeGoldVaDetras() {
        val prov = TvLibrary(listOf(ch("p", "DAZN 1", listOf(ps("a1"), ps("n1")))), emptyList(), 1)
        val out = GoldLibrary.merge(gold, prov)
        assertEquals(listOf("a1", "a2", "n1"), out.channel("g1")!!.sources.map { it.contentId })
        assertTrue(out.gold)
        assertEquals(listOf("a1", "a2", "n1"), out.sections[0].channels[0].sources.map { it.contentId })
    }

    @Test fun canalNuevoVaEnSuSeccionDetras() {
        val prov = TvLibrary(listOf(ch("p9", "Canal Raro", listOf(ps("z1")))), emptyList(), 1)
        val out = GoldLibrary.merge(gold, prov)
        assertEquals(listOf("g1", "g2", "p9"), out.channels.map { it.id })
        assertEquals(GoldLibrary.EXTRA_SECTION, out.sections.last().key)
        assertEquals(3, out.channel("p9")!!.orden)
    }

    @Test fun choqueDeIdsDeCanal() {
        val prov = TvLibrary(listOf(ch("g1", "Otro Canal", listOf(ps("z1")))), emptyList(), 1)
        val out = GoldLibrary.merge(gold, prov)
        assertEquals("prov:g1", out.channels.last().id)
        assertEquals("DAZN 1", out.channel("g1")!!.name)
    }

    @Test fun cacheReutilizaElCanalMezclado() {
        val cache = GoldLibrary.MergeCache()
        val prov = TvLibrary(listOf(ch("p", "DAZN 1", listOf(ps("n1")))), emptyList(), 1)
        val a = GoldLibrary.merge(gold, prov, cache).channel("g1")
        val b = GoldLibrary.merge(gold, prov, cache).channel("g1")
        assertSame(a, b)
    }

    @Test fun sinProveedoresEsGoldTalCual() {
        assertSame(gold, GoldLibrary.merge(gold, TvLibrary(emptyList(), emptyList(), 0)))
    }
}
