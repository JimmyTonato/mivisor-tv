package com.mivisor.tv.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPolicyTest {
    @Test fun esperaHastaLosUmbrales() {
        assertNull(StartPolicy.earlyFailure(10_000, sawStatus = true, maxPeers = 0))
        assertEquals(FailureKind.NO_PEERS, StartPolicy.earlyFailure(15_000, sawStatus = true, maxPeers = 0))
        assertNull(StartPolicy.earlyFailure(20_000, sawStatus = true, maxPeers = 3))
        assertEquals(FailureKind.NO_VIDEO, StartPolicy.earlyFailure(30_000, sawStatus = true, maxPeers = 3))
        assertEquals(FailureKind.NOT_FOUND, StartPolicy.earlyFailure(30_000, sawStatus = false, maxPeers = 0))
    }

    @Test fun losErroresDelMotorNoCulpanALaFuente() {
        assertNull(StartPolicy.fromHttpStatus(500))
        assertEquals(FailureKind.NOT_FOUND, StartPolicy.fromHttpStatus(404))
        assertEquals(FailureKind.NO_PROGRESS, StartPolicy.fromHttpStatus(503))
        assertTrue(Policy.WEIGHT.keys.containsAll(listOf(FailureKind.NOT_FOUND, FailureKind.NO_PROGRESS, FailureKind.CLIENT_ERROR, FailureKind.NO_VIDEO, FailureKind.STALLED_EARLY)))
    }

    @Test fun escalonesDeRecuperacion() {
        assertEquals(0, StartPolicy.recoveryStep(5_000))
        assertEquals(1, StartPolicy.recoveryStep(9_000))
        assertEquals(2, StartPolicy.recoveryStep(17_000))
        assertEquals(3, StartPolicy.recoveryStep(30_000))
    }

    @Test fun cicloPruebaCadaFuenteUnaVez() {
        val c = SourceCycle(listOf("a", "b", "c"))
        assertEquals("a", c.next()); assertEquals("b", c.next())
        assertEquals("a", c.choose("a"))
        assertEquals("c", c.next())
        assertNull(c.next())
        assertTrue(c.exhausted)
    }
}
