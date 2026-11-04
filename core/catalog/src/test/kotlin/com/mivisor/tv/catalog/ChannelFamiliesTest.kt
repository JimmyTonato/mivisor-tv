package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.logos.ChannelCategory
import com.mivisor.tv.catalog.tv.ChannelFamilies
import com.mivisor.tv.catalog.tv.ChannelFamily
import com.mivisor.tv.catalog.tv.NaturalOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelFamiliesTest {
    @Test fun naturalOrderPutsTwoBeforeTen() {
        val names = listOf("DAZN 10", "DAZN 2", "DAZN 1", "dazn F1", "Ámbito 3", "Ambito 20")
        assertEquals(listOf("Ámbito 3", "Ambito 20", "DAZN 1", "DAZN 2", "DAZN 10", "dazn F1"), names.sortedWith(NaturalOrder))
    }

    @Test fun familiesByName() {
        assertEquals(ChannelFamily.FOOTBALL, ChannelFamilies.of("DAZN LaLiga", ChannelCategory.SPORTS))
        assertEquals(ChannelFamily.FOOTBALL, ChannelFamilies.of("M+ Liga de Campeones 2", ChannelCategory.SPORTS))
        assertEquals(ChannelFamily.MOTOR, ChannelFamilies.of("DAZN F1", ChannelCategory.SPORTS))
        assertEquals(ChannelFamily.MOTOR, ChannelFamilies.of("M+ Vamos por MotoGP", null))
        assertEquals(ChannelFamily.SPORTS, ChannelFamilies.of("DAZN 1", ChannelCategory.SPORTS))
        assertEquals(ChannelFamily.SPORTS, ChannelFamilies.of("Eurosport 2", ChannelCategory.SPORTS))
        assertEquals(ChannelFamily.MOVIES, ChannelFamilies.of("Cine por M+", ChannelCategory.MOVIES))
        assertEquals(ChannelFamily.OTHER, ChannelFamilies.of("Movistar Plus+", null))
        // "Movistar" no es "moto" ni "motor".
        assertEquals(ChannelFamily.GENERAL, ChannelFamilies.of("Movistar", ChannelCategory.GENERAL))
    }
}

class ChannelSeriesTest {
    private fun ch(name: String) = com.mivisor.tv.catalog.tv.TvChannel(name.lowercase(), name, null, null, emptyList(), "XX")

    @Test fun groupsNumberedChannelsIntoSeries() {
        val list = listOf("M+ LaLiga 2", "M+ LaLiga 1", "M+ LaLiga 10", "M+ Liga de Campeones 1", "M+ Liga de Campeones 2", "Movistar Plus+", "DAZN 1", "DAZN 2", "DAZN F1").map(::ch)
        val series = com.mivisor.tv.catalog.tv.ChannelSeriesGrouping.group(list)
        val byName = series.associate { it.name to it.channels.map { c -> c.name } }
        assertEquals(listOf("M+ LaLiga 1", "M+ LaLiga 2", "M+ LaLiga 10"), byName["M+ LaLiga"])
        assertEquals(listOf("M+ Liga de Campeones 1", "M+ Liga de Campeones 2"), byName["M+ Liga de Campeones"])
        assertEquals(listOf("DAZN 1", "DAZN 2"), byName["DAZN"])
        assertEquals(series.joinToString { it.name + "=" + it.channels.map { c -> c.name } }, listOf("DAZN F1"), byName["DAZN F1"])
    }
}
