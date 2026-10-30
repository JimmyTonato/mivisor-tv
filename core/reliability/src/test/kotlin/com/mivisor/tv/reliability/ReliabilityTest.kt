package com.mivisor.tv.reliability

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectores generados por el modelo de referencia de la fase 2 (fiabilidad/modelo.py). */
class ReliabilityVectorsTest {
    @Test
    fun reproduceLosVectoresDelModelo() {
        val text = javaClass.classLoader!!.getResource("vectores.json")!!.readText()
        val cases = JsonParser.parseString(text).asJsonArray
        assertEquals(5, cases.size())
        for (c in cases) {
            val o = c.asJsonObject
            val s = ReliabilityStore()
            val results = ArrayList<String>()
            for (st in o.getAsJsonArray("pasos")) {
                val a = st.asJsonArray
                if (a[0].asString == "ok") {
                    s.success(a[1].asString, a[2].asDouble, a[3].asDouble); results += "ok"
                } else {
                    val ctx = if (a.size() > 5) ctxOf(a[5].asJsonObject) else FailureContext()
                    results += s.failure(a[1].asString, a[2].asDouble, a[3].asString, ctx, a[4].asString)
                }
            }
            val name = o.get("nombre").asString
            assertEquals(name, o.getAsJsonArray("resultados").map { it.asString }, results)
            val at = o.get("ordenEn").asDouble
            val cids = o.getAsJsonArray("fuentes").map { it.asString }
            assertEquals(name, o.getAsJsonArray("orden").map { it.asString }, s.order(cids, at))
            for ((cid, v) in o.getAsJsonObject("puntuaciones").entrySet()) {
                assertEquals("$name $cid", v.asDouble, s.score(cid, at), 1e-6)
            }
            for ((cid, v) in o.getAsJsonObject("estados").entrySet()) {
                assertEquals("$name $cid", v.asString, s.state(cid, at).id)
            }
        }
    }

    private fun ctxOf(o: com.google.gson.JsonObject) = FailureContext(
        online = o.get("online")?.asBoolean ?: true,
        internetOk = o.get("internet_ok")?.asBoolean ?: true,
        warpNeeded = o.get("warp_needed")?.asBoolean ?: false,
        warpOk = o.get("warp_ok")?.asBoolean ?: true,
        netChangedS = o.get("net_changed_s")?.asDouble,
        warpChangedS = o.get("warp_changed_s")?.asDouble,
        engineOk = o.get("engine_ok")?.asBoolean ?: true,
        lowMemory = o.get("low_memory")?.asBoolean ?: false,
        foreground = o.get("foreground")?.asBoolean ?: true
    )
}

/** Los escenarios principales del modelo, en Kotlin. */
class ReliabilityScenariosTest {
    private val t0 = 1_793_000_000.0
    private val day = 86400.0

    @Test fun tresFallosAbrenPausaDe10Min() {
        val s = ReliabilityStore()
        repeat(3) { assertEquals("source", s.failure("a", t0 + it * 60, FailureKind.NO_VIDEO, channel = "c1")) }
        assertEquals(SourceState.PAUSED, s.state("a", t0 + 180))
        assertEquals(t0 + 120 + 600, s.health("a")!!.pauseUntil!!, 0.0)
    }

    @Test fun aPruebaYEscalera() {
        val s = ReliabilityStore()
        repeat(3) { s.failure("a", t0 + it, FailureKind.NO_VIDEO, channel = "c1") }
        val t = t0 + 11 * 60
        s.order(listOf("a", "b"), t)
        assertEquals(SourceState.PROBATION, s.state("a", t))
        s.failure("a", t, FailureKind.NO_VIDEO, channel = "c1")
        assertEquals(t + 30 * 60, s.health("a")!!.pauseUntil!!, 0.0)
    }

    @Test fun laPausaNuncaPasaDe12h() {
        val s = ReliabilityStore(); var t = t0
        repeat(12) {
            repeat(3) { s.failure("a", t, FailureKind.NO_VIDEO, channel = "c1"); t += 1 }
            val until = s.health("a")!!.pauseUntil!!
            assertTrue(until - (t - 1) <= 720 * 60 + 5)
            t = until + 1
            s.order(listOf("a"), t)
        }
    }

    @Test fun fallosGeneralesNoPenalizan() {
        val s = ReliabilityStore()
        assertEquals("offline", s.failure("a", t0, FailureKind.NO_VIDEO, FailureContext(online = false)))
        assertEquals("warp", s.failure("a", t0, FailureKind.NO_PEERS, FailureContext(warpNeeded = true, warpOk = false)))
        assertEquals("engine", s.failure("a", t0, FailureKind.NO_VIDEO, FailureContext(engineOk = false)))
        assertEquals("device", s.failure("a", t0, FailureKind.NO_VIDEO, FailureContext(lowMemory = true)))
        assertEquals("background", s.failure("a", t0, FailureKind.NO_VIDEO, FailureContext(foreground = false)))
        assertEquals(0.0, s.health("a")!!.n, 0.0)
    }

    @Test fun variosCanalesALaVezEsFalloGeneralYSeDeshace() {
        val s = ReliabilityStore()
        assertEquals("source", s.failure("a", t0, FailureKind.NO_VIDEO, channel = "c1"))
        assertEquals("source", s.failure("b", t0 + 20, FailureKind.NO_VIDEO, channel = "c2"))
        assertEquals("global", s.failure("c", t0 + 40, FailureKind.NO_VIDEO, channel = "c3"))
        for (c in listOf("a", "b", "c")) assertEquals(0.0, s.health(c)!!.n, 0.0)
    }

    @Test fun unCanalMuertoSiSePenaliza() {
        val s = ReliabilityStore()
        for (c in listOf("a", "b", "c")) assertEquals("source", s.failure(c, t0, FailureKind.NOT_FOUND, channel = "c1"))
    }

    @Test fun cuarentenaLarga() {
        val s = ReliabilityStore()
        for (d in listOf(0.0, 1.0, 3.1)) s.failure("a", t0 + d * day, FailureKind.NOT_FOUND, channel = "c1")
        assertEquals(SourceState.QUARANTINE, s.state("a", t0 + 3.2 * day))
        var t = t0 + 3.1 * day
        repeat(8) {
            t = s.health("a")!!.lqNext!! + 1
            assertEquals(SourceState.QUARANTINE_DUE, s.state("a", t))
            s.failure("a", t, FailureKind.NOT_FOUND, channel = "c1")
            assertTrue(s.health("a")!!.lqNext!! - t <= 7 * day)
        }
        s.success("a", s.health("a")!!.lqNext!! + 1, 5000.0)
        assertEquals(SourceState.NORMAL, s.state("a", t + 30 * day))
    }

    @Test fun nuncaSinCandidata() {
        val s = ReliabilityStore()
        repeat(3) { s.failure("a", t0 + it, FailureKind.NO_VIDEO, channel = "c1") }
        repeat(3) { s.failure("b", t0 + 600 + it, FailureKind.NO_VIDEO, channel = "c1") }
        assertEquals(listOf("a", "b"), s.order(listOf("a", "b"), t0 + 700))
    }

    @Test fun desempates() {
        val s = ReliabilityStore()
        s.success("a", t0, 5000.0); s.success("b", t0, 5000.0); s.success("c", t0, 3000.0)
        assertEquals(listOf("b", "c", "a"), s.order(listOf("a", "b", "c"), t0 + 60, quality = mapOf("a" to 2, "b" to 3, "c" to 2)))
    }
}
