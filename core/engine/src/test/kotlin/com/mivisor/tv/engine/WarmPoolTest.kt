package com.mivisor.tv.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmPoolTest {
    @Test fun noPasaDeLaCapacidadYSueltaLaMasAntigua() {
        val p = WarmPool(capacity = 2, ttlMs = 60_000)
        assertEquals(emptyList<String>(), p.add("a", 0))
        assertEquals(emptyList<String>(), p.add("b", 1_000))
        assertEquals(listOf("a"), p.add("c", 2_000))
        assertTrue(p.contains("b") && p.contains("c"))
        // Volver a calentar una la pone como la más reciente.
        assertEquals(emptyList<String>(), p.add("b", 3_000))
        assertEquals(listOf("c"), p.add("d", 4_000))
    }

    @Test fun caducanSiNoSeUsanYLaQueSeVeNoSeSuelta() {
        val p = WarmPool(capacity = 2, ttlMs = 60_000)
        p.add("a", 0)
        p.add("b", 30_000)
        assertTrue(p.take("a"))                 // el reproductor la abrió
        assertEquals(emptyList<String>(), p.expired(61_000))
        assertEquals(listOf("b"), p.expired(90_000))
        assertFalse(p.contains("a"))
        assertEquals(0, p.size)
    }
}
