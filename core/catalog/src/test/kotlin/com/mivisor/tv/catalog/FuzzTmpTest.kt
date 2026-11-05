package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.tv.ChannelSeriesGrouping
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.Catalog
import com.mivisor.tv.model.Channel
import com.mivisor.tv.model.ChannelKind
import com.mivisor.tv.model.Section
import org.junit.Test

class FuzzTmpTest {
    @Test fun fuzz() {
        val parts = listOf("", " ", "1", "2", "10", "0", "M+", "Movistar", "LaLiga", "DAZN", "HD", "FHD", "4K", "1080p", "|", "-", "(", ")", "[", "]",
            "Liga de Campeones", "F1", "Eurosport", "+", "★", "ES", "🇪🇸", "Multiaudio", "VO", "3", ".", "/", "ñ", "Á", "TV", "Bar", "Ch", "#", "*", "Ⅱ", "²", "١")
        val r = java.util.Random(7)
        var n = 0
        repeat(1500) { round ->
            val provs = (0 until 12).map { p ->
                val list = (0 until 40).map {
                    val name = (0 until r.nextInt(5)).joinToString(if (r.nextBoolean()) " " else "") { parts[r.nextInt(parts.size)] }
                    Channel("c${n++}", name, "acestream://" + "%040x".format(n), null, ChannelKind.ACE, "", 0)
                }
                ProviderCatalog("p$p", "P$p", Catalog(listOf(Section("s", "S", list)), list.size))
            }
            try {
                val lib = TvLibrary.build(provs)
                ChannelSeriesGrouping.group(lib.channels)
                TvLibrary.sorted(lib.channels); lib.providers; TvLibrary.familiesOf(lib.channels)
            } catch (e: Throwable) {
                println("FALLO ronda $round: $e"); e.printStackTrace(); throw e
            }
        }
    }
}
