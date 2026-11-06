package com.mivisor.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnChannelsTest {
    private val now = 1_762_458_000_000L
    private fun ev(id: String, startMin: Long, vararg ch: String) =
        AgendaEvent(id, "futbol", "LaLiga", now + startMin * 60_000, "A", "B", "A – B", ch.map { AgendaChannel(it, null, null) }, null, null)

    @Test fun ahoraYDespuesPorCanal() {
        val calls = HashMap<String, Int>()
        val map = OnChannels.build(
            listOf(ev("vivo", -40, "M+ LaLiga 2"), ev("luego", 80, "M+ LaLiga 2", "DAZN LaLiga"), ev("antes", 30, "DAZN LaLiga"),
                ev("fin", -400, "M+ LaLiga 2"), ev("lejos", 60 * 30, "M+ LaLiga 3")),
            now, match = { name -> calls[name] = (calls[name] ?: 0) + 1; listOf("id:$name") }
        )
        assertEquals("vivo", map.getValue("id:M+ LaLiga 2").now?.id)
        assertEquals("luego", map.getValue("id:M+ LaLiga 2").next?.id)
        assertNull(map.getValue("id:DAZN LaLiga").now)
        assertEquals("antes", map.getValue("id:DAZN LaLiga").next?.id)
        assertNull(map["id:M+ LaLiga 3"])                                 // a más de 24 h: nada
        assertEquals(1, calls["M+ LaLiga 2"])                             // cada nombre se empareja una vez
        assertEquals(0.33f, ev("x", -40).progress(now), 0.01f)
    }
}
