package com.mivisor.tv.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPolicyTest {
    @Test fun esperaHastaLosUmbrales() {
        // 3e.1: un minuto completo antes de pasar a otra fuente, haya emisores o no.
        assertNull(StartPolicy.earlyFailure(15_000, sawStatus = true, maxPeers = 0))
        assertNull(StartPolicy.earlyFailure(30_000, sawStatus = true, maxPeers = 3))
        assertNull(StartPolicy.earlyFailure(59_000, sawStatus = false, maxPeers = 0))
        assertEquals(FailureKind.NO_PEERS, StartPolicy.earlyFailure(60_000, sawStatus = true, maxPeers = 0))
        assertEquals(FailureKind.NO_VIDEO, StartPolicy.earlyFailure(60_000, sawStatus = true, maxPeers = 3))
        assertEquals(FailureKind.NOT_FOUND, StartPolicy.earlyFailure(60_000, sawStatus = false, maxPeers = 0))
        assertNull(StartPolicy.earlyFailure(100_000, sawStatus = true, maxPeers = 3, timeoutMs = 120_000))
        assertEquals(60_000L, StartPolicy.switchAfter(30_000))
        assertEquals(120_000L, StartPolicy.switchAfter(120_000))
        assertEquals(Long.MAX_VALUE, StartPolicy.switchAfter(0))
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
