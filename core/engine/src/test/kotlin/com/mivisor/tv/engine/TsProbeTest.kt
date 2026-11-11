package com.mivisor.tv.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4e · Caja negra: resumen del vídeo de un canal. */
class TsProbeTest {
    private fun packet(pid: Int, pusi: Boolean, payload: ByteArray, rai: Boolean = false): ByteArray {
        val p = ByteArray(188) { 0xFF.toByte() }
        p[0] = 0x47; p[1] = ((if (pusi) 0x40 else 0) or (pid shr 8)).toByte(); p[2] = pid.toByte()
        var o = 4
        if (rai) { p[3] = 0x30; p[4] = 1; p[5] = 0x40; o = 6 } else p[3] = 0x10
        payload.copyInto(p, o, 0, minOf(payload.size, 188 - o))
        return p
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** PAT → PMT 0x1000; PMT: vídeo [videoType] en 0x100 y MP2 en 0x101. */
    private fun tables(videoType: Int): ByteArray {
        val pat = bytes(0, 0x00, 0xB0, 13, 0, 1, 0xC1, 0, 0, 0, 1, 0xF0, 0x00, 0, 0, 0, 0)
        val pmt = bytes(0, 0x02, 0xB0, 23, 0, 1, 0xC1, 0, 0, 0xE1, 0x00, 0xF0, 0x00,
            videoType, 0xE1, 0x00, 0xF0, 0x00, 0x03, 0xE1, 0x01, 0xF0, 0x00, 0, 0, 0, 0)
        return packet(0, true, pat) + packet(0x1000, true, pmt)
    }

    private fun pes(es: ByteArray) = bytes(0, 0, 1, 0xE0, 0, 0, 0x80, 0x00, 0x00) + es

    @Test fun h264SinIdrSeVeComoISinIdr() {
        val key = pes(bytes(0, 0, 1, 0x09, 0x10, 0, 0, 1, 0x67, 0x64, 0, 0x28, 0, 0, 1, 0x68, 0xEE, 0, 0, 1, 0x01, 0x88, 0x80))
        val p = pes(bytes(0, 0, 1, 0x09, 0x30, 0, 0, 1, 0x01, 0x98))
        val r = TsProbe.analyze(tables(0x1B) + packet(0x100, true, key) + packet(0x100, true, p) + packet(0x100, true, key))
        assertEquals("H.264", r.videoCodec)
        assertEquals(mapOf("I sin IDR" to 2), r.keyframes)
        assertEquals(3, r.pesStarts)
        assertTrue(r.summary(), r.summary().startsWith("H.264 + MP2"))
    }

    @Test fun hevcConIdrYMarca() {
        val key = pes(bytes(0, 0, 1, 0x40, 0x01, 0, 0, 1, 0x42, 0x01, 0, 0, 1, 0x28, 0x01))
        val r = TsProbe.analyze(tables(0x24) + packet(0x100, true, key, rai = true))
        assertEquals("HEVC", r.videoCodec)
        assertEquals(mapOf("IDR" to 1), r.keyframes)
        assertEquals(1, r.randomAccess)
    }

    @Test fun sinPuntosDeArranqueYDanado() {
        val p = pes(bytes(0, 0, 1, 0x09, 0x30, 0, 0, 1, 0x01, 0x98))
        val broken = packet(0x100, false, p).also { it[0] = 0x12 }
        val r = TsProbe.analyze(tables(0x1B) + packet(0x100, true, p) + broken + packet(0x100, true, p))
        assertTrue(r.summary(), "NINGÚN punto de arranque" in r.summary())
        assertEquals(1, r.syncErrors)
    }

    @Test fun nadaQueAnalizar() {
        assertTrue(TsProbe.analyze(ByteArray(0)).summary().startsWith("sin vídeo"))
    }
}
