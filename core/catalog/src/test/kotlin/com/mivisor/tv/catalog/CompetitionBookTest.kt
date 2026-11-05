package com.mivisor.tv.catalog

import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.CompetitionNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CompetitionBookTest {
    private fun ev(id: String, comp: String?, sport: String = "futbol") =
        AgendaEvent(id, sport, comp, 0L, "A", "B", "A - B", emptyList(), null, null)

    @Test fun clavesYCoincidencias() {
        assertEquals("raw:copa libertadores", CompetitionNames.id(ev("1", "Copa Libertadores")))
        assertEquals("raw:copa libertadores", CompetitionNames.id(ev("2", " COPA  LIBERTADORES ")))
        assertEquals("raw:deporte:tenis", CompetitionNames.id(ev("3", null, "tenis")))
        assertTrue(CompetitionNames.matches("raw:copa libertadores", ev("4", "Copa Libertadores")))
        assertFalse(CompetitionNames.matches("raw:copa libertadores", ev("5", "Copa Sudamericana")))
        assertTrue(CompetitionNames.matches("LALIGA", ev("6", "LaLiga EA Sports")))
    }

    @Test fun guardaOlvidaYConservaLasElegidas() {
        val file = File(Files.createTempDirectory("comp").toFile(), "competitions.json")
        var now = 1_000_000L
        val book = CompetitionBook(file) { now }
        book.update(listOf(ev("1", "Copa Libertadores"), ev("2", "Copa Libertadores"), ev("3", "Liga MX"), ev("4", "Premier League")))
        assertEquals(3, book.all().size)
        assertEquals(2, book.all().first { it.id == "raw:copa libertadores" }.events)

        // Otra agenda sin la Libertadores: sigue en la lista (con 0 eventos) y se lee igual desde disco.
        now += 3_600_000L
        book.update(listOf(ev("5", "Premier League")))
        val fromDisk = CompetitionBook(file) { now }.all()
        assertEquals(3, fromDisk.size)
        assertEquals(0, fromDisk.first { it.id == "raw:copa libertadores" }.events)
        assertEquals("Copa Libertadores", fromDisk.first { it.id == "raw:copa libertadores" }.name)

        // 100 días después se olvida… salvo la que está oculta o seguida.
        now += 100L * 24 * 3600_000L
        val after = book.update(listOf(ev("6", "Premier League")), keep = setOf("raw:copa libertadores"))
        assertEquals(setOf("raw:copa libertadores", "raw:premier league"), after.map { it.id }.toSet())
    }
}
