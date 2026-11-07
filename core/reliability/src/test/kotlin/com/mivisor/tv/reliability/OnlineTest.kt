package com.mivisor.tv.reliability

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineTest {
    private val t0 = 1_800_000_000.0

    @Test fun enLineaSoloSiFuncionoHacePocoYNoFalloDespues() {
        val s = ReliabilityStore()
        assertFalse("sin probar: no", s.online("a", t0))
        s.success("a", t0, 3_000.0)
        assertTrue("acaba de dar imagen", s.online("a", t0 + 60))
        assertFalse("hace más de 12 h", s.online("a", t0 + 13 * 3600))
        s.success("b", t0, 4_000.0)
        s.probeFailed("b", t0 + 120)
        assertFalse("el verificador no vio imagen después", s.online("b", t0 + 180))
        s.success("c", t0, 4_000.0)
        s.failure("c", t0 + 100, "fatal")
        assertFalse("falló al verla después", s.online("c", t0 + 200))
    }
}
