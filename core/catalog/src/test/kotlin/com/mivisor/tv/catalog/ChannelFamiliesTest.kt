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
