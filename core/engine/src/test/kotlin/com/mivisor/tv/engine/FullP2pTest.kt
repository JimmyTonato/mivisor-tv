package com.mivisor.tv.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3s · «Aceptar conexiones de otros (P2P completo)». */
class FullP2pTest {
    @Test fun porDefectoSoloSaliente() {
        val cfg = EngineConfig(16900, "/d", "/h", lowRam = false)
        val env = cfg.environment()
        assertEquals("127.0.0.1:0", env["OUTPACE_PEER_LISTEN"])
        assertEquals("0", env["OUTPACE_ENABLE_INBOUND"])
        assertEquals("0", env["OUTPACE_ENABLE_SEEDING"])
        assertEquals("0", env["OUTPACE_ENABLE_PORT_MAPPING"])
        assertEquals("none", env["OUTPACE_PORT_MAP_BACKEND"])
        assertTrue(cfg.validate().isEmpty())
    }

    @Test fun completoAceptaConexionesYAbrePuerto() {
        val cfg = EngineConfig(16900, "/d", "/h", lowRam = false, fullP2p = true, peerPort = 18621)
        val env = cfg.environment()
        assertEquals("0.0.0.0:18621", env["OUTPACE_PEER_LISTEN"])
        assertEquals("1", env["OUTPACE_ENABLE_INBOUND"])
        assertEquals("1", env["OUTPACE_ENABLE_SEEDING"])
        assertEquals("1", env["OUTPACE_ENABLE_PORT_MAPPING"])
        assertEquals("auto", env["OUTPACE_PORT_MAP_BACKEND"])
        assertEquals("8", env["OUTPACE_MAX_INBOUND"])
        assertEquals("127.0.0.1:16900", env["OUTPACE_BIND"])          // la API sigue solo en local
        assertTrue(cfg.validate().isEmpty())
    }

    @Test fun pocaMemoriaMenosEntrantes() {
        val env = EngineConfig(16900, "/d", "/h", lowRam = true, fullP2p = true, peerPort = 18621).environment()
        assertEquals("4", env["OUTPACE_MAX_INBOUND"])
    }

    @Test fun puertoP2pInvalidoSeRechaza() {
        assertTrue(EngineConfig(16900, "/d", "/h", false, fullP2p = true, peerPort = 0).validate().any { "P2P" in it })
        assertTrue(EngineConfig(16900, "/d", "/h", false, fullP2p = true, peerPort = 16900).validate().any { "P2P" in it })
    }
}
