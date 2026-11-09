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
        val s = Sim(delay = 40_000, ahead = 36_000)
        s.run(60)                                             // estable y con todo bajado: empieza a acercarse
        assertTrue(s.actions.isNotEmpty())
        val before = s.actions.size
        s.run(4, cut = true)
        assertTrue("al cortarse se deja de acelerar", s.speed == 1f)
        s.run(20)                                             // 20 s: no basta (hace falta más estabilidad tras un corte)
        assertEquals("nada de recortes justo después de un corte", 0,
            s.actions.drop(before).count { it is Action.SeekForward || it == Action.SetSpeed(1.1f) })
    }

    @Test fun conLaRedBienSeAcercaDe10En10HastaUnos10s() {
        val s = Sim(delay = 70_000, ahead = 66_000)
        s.run(240)                                            // 4 min, la red sigue el ritmo del directo
        val seeks = s.actions.filterIsInstance<Action.SeekForward>()
        assertTrue("salta dentro de lo descargado: $seeks", seeks.isNotEmpty())
        assertTrue("pruebas de 10 s como mucho: $seeks", seeks.all { it.ms <= 10_000 })
        assertTrue("se ha quedado cerca del directo: ${s.delay}", s.delay in 10_000..12_000)
    }

    @Test fun conLaRedJustaNoPruebaNada() {
        val s = Sim(delay = 40_000, ahead = 20_000)
        s.run(240, aheadGrowth = 1_000)                       // descarga justo lo que se ve, lejos del directo
        assertTrue("sin pruebas con la red justa: ${s.actions}", s.actions.isEmpty())
    }

    @Test fun unaPruebaQueSaleMalHaceEsperarElDoble() {
        val c = CatchUpController()
        c.reset(0)
        var t = 0L
        var delay = 50_000L
        fun tick(): CatchUpController.Action { t += 1_000; return c.onTick(t, delay, delay - 2_000, 6_000, true) }
        var first = -1L
        while (first < 0 && t < 120_000) { if (tick() is CatchUpController.Action.SeekForward) { first = t; delay -= 10_000 } }
        assertTrue("primera prueba a los ~30 s: $first", first in 30_000..32_000)
        c.onStall(t + 5_000); t += 9_000; c.onResume(t); delay += 4_000   // corte justo después: salió mal
        var second = -1L
        val from = t
        while (second < 0 && t < from + 300_000) { if (tick() is CatchUpController.Action.SeekForward) second = t }
        assertTrue("tras una prueba mala espera más (≥ 60 s): ${second - from}", second - from >= 60_000)
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
        assertEquals("sin cortes: el preferido, 10 s", 10_000L, CatchUpController().also { it.reset(0) }.reserveMs(1_000, 6_000))
    }
}

class CatchUpMarginTest {
    /** Simulación con red de [rate] × lo que se ve (1,0 = justa; 1,3 = sobrada). */
    private fun run(rate: Double, delay0: Long, ahead0: Long, seconds: Int): Pair<Long, List<CatchUpController.Action>> {
        val c = CatchUpController()
        c.reset(0)
        var delay = delay0
        var ahead = ahead0
        var speed = 1f
        val acts = ArrayList<CatchUpController.Action>()
        var now = 0L
        repeat(seconds) {
            now += 1_000
            val extra = ((speed - 1f) * 1_000).toLong()
            delay -= extra
            ahead = (ahead + (rate * 1_000).toLong() - 1_000 - extra).coerceIn(0, delay)
            when (val a = c.onTick(now, delay, ahead, 6_000, true)) {
                is CatchUpController.Action.SetSpeed -> { speed = a.speed; acts += a }
                is CatchUpController.Action.SeekForward -> { delay -= a.ms; ahead -= a.ms; acts += a }
                CatchUpController.Action.None -> Unit
            }
        }
        return delay to acts
    }

    @Test fun conRedSobradaRecortaMasYAntes() {
        val (sobrada, a1) = run(rate = 1.3, delay0 = 60_000, ahead0 = 20_000, seconds = 150)
        val (justa, a2) = run(rate = 1.0, delay0 = 60_000, ahead0 = 20_000, seconds = 150)
        assertTrue("con red sobrada se acerca más: $sobrada vs $justa", sobrada < justa)
        assertTrue("con red justa se conserva el colchón descargado ($a2)", a2.filterIsInstance<CatchUpController.Action.SeekForward>().sumOf { it.ms } <= 14_000)
        assertTrue(a1.isNotEmpty())
    }
}

/** 3p · Primero la fluidez: con la red a rachas no se vuelve al directo, y nunca se sale de la ventana del motor. */
class CatchUpFluidityTest {
    @Test fun conCortesARachasNoVuelveAlDirecto() {
        val c = CatchUpController()
        c.reset(0)
        var t = 1_000L
        repeat(3) { c.onStall(t); t += 4_000; c.onResume(t); t += 20_000 }
        // Mucho retraso y mucha imagen descargada, y estable 3 min: aun así, con 3 cortes recientes, no recorta.
        val acts = ArrayList<CatchUpController.Action>()
        repeat(180) { t += 1_000; acts += c.onTick(t, 40_000, 35_000, 6_000, true) }
        assertTrue("sin recortes con la red a rachas: ${acts.filter { it != CatchUpController.Action.None }}", acts.all { it == CatchUpController.Action.None })
    }

    @Test fun nuncaSeSaleDeLaVentanaDelMotor() {
        val c = CatchUpController()
        c.reset(0)
        c.maxDelayMs = 46_000
        val a = c.onTick(1_000, 50_000, 2_000, 6_000, true)          // sin estabilidad ni imagen por delante
        assertTrue("adelanta lo justo para seguir dentro: $a", a is CatchUpController.Action.SeekForward && a.ms == 50_000L - (46_000 - 8_000))
        assertEquals(CatchUpController.Action.None, CatchUpController().also { it.reset(0); it.maxDelayMs = 46_000 }.onTick(1_000, 30_000, 2_000, 6_000, true))
    }

    @Test fun elColchonNoPasaDeLaVentana() {
        val c = CatchUpController()
        c.reset(0)
        c.maxDelayMs = 28_000
        var t = 1_000L
        repeat(3) { c.onStall(t); t += 15_000; c.onResume(t); t += 5_000 }
        assertEquals(20_000L, c.reserveMs(t, 6_000))                   // 28 s − 8 s de margen
    }
}
