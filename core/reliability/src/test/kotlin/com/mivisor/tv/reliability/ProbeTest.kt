package com.mivisor.tv.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3h.1 · Lo que aprende el verificador («Comprobar ahora») cambia el orden de prueba, sin castigar de más. */
class ProbeTest {
    @Test fun laQueFuncionaVaPrimeroYLaQueFallaNoSeCastiga() {
        val s = ReliabilityStore()
        val now = 1_000_000.0
        // Sin datos: el orden de la lista (1, 2, 3, 4).
        assertEquals(listOf("a", "b", "c", "d"), s.order(listOf("a", "b", "c", "d"), now))
        // El verificador: la 1 y la 2 no dan imagen, la 4 sí.
        s.probeFailed("a", now)
        s.probeFailed("b", now)
        s.success("d", now, 4_000.0)
        val order = s.order(listOf("a", "b", "c", "d"), now + 60)
        assertEquals("primero la que el verificador vio funcionar", "d", order.first())
        assertTrue("las que fallaron, detrás de la desconocida: $order", order.indexOf("c") < order.indexOf("a"))
        // Una prueba fallida no pausa ni manda a cuarentena.
        assertEquals(SourceState.NORMAL, s.state("a", now + 60))
    }

    @Test fun recordarLaUltimaQueFuncionoGana() {
        val s = ReliabilityStore()
        val now = 2_000_000.0
        listOf("a", "b", "c", "d").forEach { s.success(it, now, 6_000.0) }
        // El canal recuerda la 4.ª: se prueba primero aunque todas tengan buena nota.
        assertEquals("d", s.order(listOf("a", "b", "c", "d"), now + 10, remembered = "d").first())
    }
}
