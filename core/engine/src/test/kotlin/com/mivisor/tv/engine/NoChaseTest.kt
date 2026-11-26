package com.mivisor.tv.engine

import com.mivisor.tv.engine.CatchUpController.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4r · Sin acercamiento automático: la app no salta ni acelera; solo la guarda de la ventana del motor. */
class NoChaseTest {
    @Test fun sinAcercamientoNoSeMueveAunqueLaRedVayaBien() {
        val c = CatchUpController().also { it.maxDelayMs = 46_000; it.reset(0) }
        var t = 0L
        val acts = ArrayList<Action>()
        repeat(600) {
            t += 1_000
            // 30 s de retraso, todo descargado hasta el directo: antes se habría acercado.
            val a = c.onTick(t, 30_000, 29_000, 6_000, true, approach = false)
            if (a != Action.None) acts += a
        }
        assertTrue(acts.isEmpty())
        assertEquals(CatchUpController.State.WAITING, c.stats.state)
    }

    @Test fun laGuardaDeLaVentanaSigueActuando() {
        val c = CatchUpController().also { it.maxDelayMs = 46_000; it.reset(0) }
        val a = c.onTick(1_000, 50_000, 10_000, 6_000, true, approach = false)
        assertTrue(a is Action.SeekForward)
        // Se adelanta lo justo para quedar 8 s dentro de la ventana: 50 − (46 − 8) = 12 s.
        assertEquals(12_000L, (a as Action.SeekForward).ms)
    }

    @Test fun conAcercamientoSigueComoAntes() {
        val c = CatchUpController().also { it.maxDelayMs = 46_000; it.reset(0) }
        var t = 0L
        var moved = false
        repeat(120) {
            t += 1_000
            if (c.onTick(t, 30_000, 29_000, 6_000, true, approach = true) != Action.None) moved = true
        }
        assertTrue(moved)
    }
}
