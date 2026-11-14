package com.mivisor.tv.catalog

import com.mivisor.tv.model.Channel
import com.mivisor.tv.model.ChannelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4p · La agenda pone relleno junto al canal; el número del canal tiene que coincidir siempre. */
class MatcherNoiseTest {
    private fun ch(id: String, name: String) = Channel(id, name, id, null, ChannelKind.ACE, "XX", 0)
    private val m = ChannelMatcher(listOf(
        ch("hm", "LaLiga TV Hypermotion"), ch("hm2", "LaLiga TV Hypermotion 2"),
        ch("d", "DAZN"), ch("d1", "DAZN 1"), ch("d2", "DAZN 2"), ch("dl", "DAZN LaLiga"),
        ch("ml", "M+ LaLiga TV"), ch("ml2", "M+ LaLiga TV 2")
    ))
    private fun ids(name: String) = m.match(name, 5).map { it.id }

    @Test fun rellenoVerPartido() {
        assertEquals("hm", ids("LaLiga Hypermotion ver partido").first())
        assertEquals("hm", ids("LaLiga Hypermotion · Ver partido en directo").first())
        assertTrue("hm2" !in ids("LaLiga Hypermotion ver partido"))
    }

    @Test fun numeroExacto() {
        assertEquals(listOf("d2"), ids("DAZN 2"))
        assertTrue("d2" !in ids("DAZN"))
        assertTrue("d1" !in ids("DAZN"))
        assertEquals(listOf("dl"), ids("DAZN LaLiga ver partido"))
    }

    @Test fun dialNoCuenta() {
        assertEquals("ml", ids("M+ LaLiga Dial M54 0110").first())
        assertTrue("ml2" !in ids("Movistar LaLiga dial 54"))
    }
}
