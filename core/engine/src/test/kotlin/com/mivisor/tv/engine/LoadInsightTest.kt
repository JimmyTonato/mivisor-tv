package com.mivisor.tv.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** 4d · Mensajes claros mientras se espera la imagen con emisores. */
class LoadInsightTest {
    private fun st(downloaded: Long? = null, pos: Long? = null, last: Long? = null, rejects: Long = 0, rate: Long? = null) =
        EngineStatus(peers = 1, bitrateBps = 0, engineBufferMs = 0, upstreamReconnects = 0, staleTeardowns = 0, skippedGaps = 0,
            activeUpstreams = 1, title = null, downloaded = downloaded, downloadRate = rate, pos = pos, liveLast = last,
            signatureRejects = rejects)

    private fun LoadInsight.run(secs: Int, f: (Int) -> EngineStatus): LoadInsight.Insight {
        var r: LoadInsight.Insight? = null
        for (i in 1..secs) r = observe(f(i), 1)
        return r!!
    }

    @Test fun alPrincipioEsperaNormal() {
        assertEquals(LoadInsight.Kind.WAITING, LoadInsight().observe(st(downloaded = 0), 1).kind)
    }

    @Test fun llegaVideoYSePara_emisionParada() {
        // El caso de Movistar La Liga: 10 MB en 2 s y luego nada; la última pieza anunciada no se mueve.
        val k = LoadInsight().run(16) { i -> st(downloaded = if (i < 3) i * 5_000_000L else 10_000_000L, last = 1437056) }
        assertEquals(LoadInsight.Kind.STALLED, k.kind)
        assertEquals("La emisión está parada", k.status)
    }

    @Test fun laEmisionAvanzaPeroNoNosLlega() {
        val k = LoadInsight().run(16) { i -> st(downloaded = if (i < 3) i * 1_000_000L else 2_000_000L, last = 100L + i) }
        assertEquals(LoadInsight.Kind.NOT_REACHING, k.kind)
    }

    @Test fun conectadosSinMandarNada() {
        val k = LoadInsight().run(11) { st(downloaded = 0) }
        assertEquals(LoadInsight.Kind.NO_DATA, k.kind)
    }

    @Test fun llegaVideoSinParar_recibiendoYLuegoSinImagen() {
        val inst = LoadInsight()
        assertEquals(LoadInsight.Kind.RECEIVING, inst.run(10) { i -> st(downloaded = i * 1_000_000L) }.kind)
        val k = inst.run(25) { i -> st(downloaded = (10 + i) * 1_000_000L, rate = 1_000_000) }
        assertEquals(LoadInsight.Kind.NO_PICTURE, k.kind)
        assertEquals(true, k.hint!!.contains("8.0 Mb/s"))
    }

    @Test fun piezasQueNoPasanLaFirma() {
        val k = LoadInsight().run(5) { i -> st(downloaded = 0, rejects = i.toLong()) }
        assertEquals(LoadInsight.Kind.SIGNATURE, k.kind)
    }

    @Test fun motorSinParche_usaLaPieza() {
        // Sin «downloaded»: avanza la pieza → recibiendo; deja de avanzar → parada.
        val inst = LoadInsight()
        assertEquals(LoadInsight.Kind.RECEIVING, inst.run(3) { i -> st(pos = 10L + i) }.kind)
        assertEquals(LoadInsight.Kind.STALLED, inst.run(13) { st(pos = 13) }.kind)
    }

    @Test fun estadoLeeLosCamposNuevos() {
        val s = EngineStatus.parse("""{"peers":2,"downloaded":12345,"download_rate":800,"livepos":{"pos":5,"live_last":9},"recovery":{"signature_rejects":3}}""")!!
        assertEquals(12345L, s.downloaded)
        assertEquals(800L, s.downloadRate)
        assertEquals(5L, s.pos)
        assertEquals(9L, s.liveLast)
        assertEquals(3L, s.signatureRejects)
        assertEquals(4L, s.piecesBehindLive)
    }
}
