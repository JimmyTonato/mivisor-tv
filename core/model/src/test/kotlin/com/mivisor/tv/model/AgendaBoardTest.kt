package com.mivisor.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class AgendaBoardTest {
    private val clock = DayClock(TimeZone.getTimeZone("Europe/Madrid"))
    private val now = 1_762_458_000_000L   // jueves 6-nov-2025, 20:40 en Madrid
    private fun ev(id: String, comp: String?, startMin: Long, sport: String = "futbol") =
        AgendaEvent(id, sport, comp, now + startMin * 60_000, "A$id", "B$id", "A$id – B$id", emptyList(), null, null)

    @Test fun agrupaPorCompeticionConDirectoYSeguidasDelante() {
        val events = listOf(
            ev("1", "LaLiga", 80), ev("2", "Champions League", -30), ev("3", "LaLiga", -20),
            ev("4", "Copa Libertadores", 60), ev("5", "Premier League", 30), ev("6", "Champions League", 50),
            ev("7", null, 10, sport = "tenis"), ev("8", "LaLiga", -300)      // terminado: fuera
        )
        val b = AgendaBoard.build(events, now, clock, 0, null, followed = setOf("raw:copa libertadores"), categories = listOf("futbol", "tenis"))
        assertEquals(listOf("Champions League", "LaLiga", "Copa Libertadores", "Otros:tenis", "Premier League"),
            b.groups.map { if (it.id.startsWith("raw:deporte:")) "Otros:tenis" else it.name })
        assertEquals(listOf("3", "1"), b.groups[1].events.map { it.id })          // en directo primero
        assertEquals(1, b.groups[1].live)
        assertEquals(listOf("futbol" to 6, "tenis" to 1), b.sports)
    }

    @Test fun filtraDeporteYCuentaDias() {
        val events = listOf(ev("1", "LaLiga", 30), ev("2", "ATP", 40, sport = "tenis"), ev("3", "LaLiga", 26 * 60))
        val b = AgendaBoard.build(events, now, clock, 0, "futbol", emptySet())
        assertEquals(listOf("1"), b.events.map { it.id })
        assertEquals(1, b.perDay[0]); assertEquals(1, b.perDay[1])
        assertEquals(2, b.sports.size)                                              // los chips de deporte no se filtran
        val manana = AgendaBoard.build(events, now, clock, 1, "futbol", emptySet())
        assertEquals(listOf("3"), manana.events.map { it.id })
    }
}
