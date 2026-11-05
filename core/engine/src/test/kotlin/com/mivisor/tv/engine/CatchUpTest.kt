package com.mivisor.tv.engine

import com.mivisor.tv.engine.CatchUpController.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Simulación segundo a segundo: retraso, imagen descargada por delante y cortes. */
class CatchUpTest {
    private class Sim(var delay: Long = 6_000, var ahead: Long = 6_000) {
        val c = CatchUpController()
        var now = 0L
        var speed = 1f
        var cutting = false
        val actions = ArrayList<Action>()
        val target = 6_000L

        init { c.reset(0) }

        /** [n] segundos; con [cut] la imagen está parada (el retraso crece, no se descarga nada útil). */
        fun run(n: Int, cut: Boolean = false, aheadGrowth: Long = 1_000) {
            repeat(n) {
                now += 1_000
                if (cut && !cutting) { cutting = true; c.onStall(now).let { if (it is Action.SetSpeed) speed = it.speed } }
                if (!cut && cutting) { cutting = false; c.onResume(now) }
                if (cut) delay += 1_000
                else {
                    val extra = ((speed - 1f) * 1_000).toLong()
                    delay -= extra
                    ahead = (ahead + aheadGrowth - 1_000 - extra).coerceAtLeast(0).coerceAtMost(delay)
                }
                val a = c.onTick(now, delay, ahead, target, playing = !cut)
                when (a) {
                    is Action.SetSpeed -> speed = a.speed
                    is Action.SeekForward -> { delay -= a.ms; ahead -= a.ms }
                    Action.None -> Unit
                }
                if (a != Action.None) actions += a
            }
        }
    }

    @Test fun losCortesNoMuevenAlUsuario() {
        val s = Sim()
        s.run(20)
        repeat(4) { s.run(5, cut = true); s.run(8) }      // corta, vuelve, corta, vuelve…
        assertTrue("sin saltos durante los cortes: ${s.actions}", s.actions.none { it is Action.SeekForward })
        assertEquals(26_000L, s.delay)                        // 6 s + 4 cortes de 5 s: sigue donde estaba
    }

    @Test fun conEstabilidadSeAcercaPocoAPocoYDejaColchon() {
        val s = Sim(delay = 6_000, ahead = 6_000)
        s.run(10)
        s.run(6, cut = true)                                  // un corte de 6 s → colchón ≈ 12 s
        s.run(1)
        s.delay = 30_000; s.ahead = 26_000                    // 30 s de retraso, casi todo ya descargado
        s.run(25)
        assertTrue("aún no: espera a estar estable (${s.actions})", s.actions.none { it is Action.SeekForward || it == Action.SetSpeed(1.1f) })
        s.run(240, aheadGrowth = 1_100)                       // 4 min estable, descargando algo más rápido que se ve
        assertTrue("se ha acercado: ${s.delay}", s.delay < 20_000)
        assertTrue("nunca por debajo del colchón aprendido (12 s): ${s.delay}", s.delay >= 11_500)
        val firstStep = s.actions.first { it is Action.SeekForward || it is Action.SetSpeed }
        assertTrue("el primer recorte es proporcional, no de golpe: $firstStep",
            firstStep is Action.SetSpeed || (firstStep as Action.SeekForward).ms <= 10_000)
    }

    @Test fun unCorteSuspendeLaVuelta() {
        val s = Sim(delay = 40_000, ahead = 30_000)
        s.run(60)                                             // estable: empieza a acercarse
        assertTrue(s.actions.isNotEmpty())
        val before = s.actions.size
        s.run(4, cut = true)
        assertTrue("al cortarse se deja de acelerar", s.speed == 1f)
        s.run(20)                                             // 20 s: no basta (hace falta más estabilidad tras un corte)
        assertEquals("nada de recortes justo después de un corte", 0,
            s.actions.drop(before).count { it is Action.SeekForward || it == Action.SetSpeed(1.1f) })
    }

    @Test fun conMuchoRetrasoYMuchaImagenRecortaMas() {
        val s = Sim(delay = 70_000, ahead = 64_000)
        s.run(200)                                            // > 3 min estable
        val seeks = s.actions.filterIsInstance<Action.SeekForward>()
        assertTrue("salta dentro de lo descargado: $seeks", seeks.isNotEmpty())
        assertTrue("con mucho retraso, recortes grandes (≥ 15 s): $seeks", seeks.maxOf { it.ms } >= 15_000)
        assertTrue("sin llegar al borde: ${s.delay}", s.delay >= 6_000)
    }

    @Test fun sinImagenDescargadaNoSaltaANegro() {
        val s = Sim(delay = 40_000, ahead = 1_000)
        s.run(120, aheadGrowth = 1_000)                       // descarga justo lo que se ve: nunca hay de sobra
        assertTrue("ni salta ni acelera sin imagen por delante: ${s.actions}", s.actions.isEmpty())
        assertEquals(40_000L, s.delay)
    }

    @Test fun elColchonCreceConCortesSeguidos() {
        val c = CatchUpController()
        c.reset(0)
        var t = 1_000L
        repeat(3) {
            c.onStall(t); t += 7_000; c.onResume(t); t += 10_000
        }
        val r = c.reserveMs(t, 6_000)
        assertTrue("3 cortes de 7 s → colchón 7 × 2 × 1,5 = 21 s: $r", r in 20_000..22_000)
        assertEquals(6_000L, CatchUpController().also { it.reset(0) }.reserveMs(1_000, 6_000))
    }
}
