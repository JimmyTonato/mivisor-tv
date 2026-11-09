package com.mivisor.tv.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 3t · Líneas «[mivisor]» del motor (parche 0002 de Outpace) → resumen de vías. */
class ViasTest {
    private val d = EngineDiagnostics

    @Test fun leeLasTresLineas() {
        var v = EngineDiagnostics.Vias()
        v = d.parseVia(v, "outpace: [mivisor] trackers del descriptor: 3 (udp=1 http=2 otros=0) t1:6969 t2:80 t3")
        v = d.parseVia(v, "outpace: [mivisor] pares por tracker: udp=0 http=4 (de 3 trackers)")
        v = d.parseVia(v, "outpace: [mivisor] pares por dht: 0")
        assertEquals("3 (udp=1 http=2 otros=0) t1:6969 t2:80 t3", v.trackers)
        assertEquals("udp=0 http=4 (de 3 trackers)", v.porTracker)
        assertEquals("0", v.porDht)
    }

    @Test fun ignoraLoDemas() {
        val v = d.parseVia(EngineDiagnostics.Vias(), "outpace: inbound seeding ENABLED on 0.0.0.0:18621")
        assertNull(v.trackers); assertNull(v.porTracker); assertNull(v.porDht)
    }
}
