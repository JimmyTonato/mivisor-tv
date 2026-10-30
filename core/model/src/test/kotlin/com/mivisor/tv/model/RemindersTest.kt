package com.mivisor.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val t0 = 1_800_000_000_000L
    private fun r(id: String, start: Long, notified: Boolean = false) = Reminder(id, "Evento $id", start, "futbol", null, listOf("DAZN LaLiga"), notified)

    @Test fun avisaCincoMinutosAntesYSoloUnaVez() {
        val list = listOf(r("a", t0 + 10 * 60_000), r("b", t0 + 4 * 60_000), r("c", t0 + 2 * 60_000, notified = true))
        assertEquals(listOf("b"), ReminderPolicy.due(list, t0).map { it.eventId })
        assertEquals(listOf("b", "a"), ReminderPolicy.due(list, t0 + 6 * 60_000).map { it.eventId })
    }

    @Test fun alAbrirLaAppAvisaDeLosQueYaEmpezaronYCaducan() {
        val list = listOf(r("a", t0 - 20 * 60_000), r("b", t0 - 4 * 3600_000L))
        assertEquals(listOf("a"), ReminderPolicy.due(list, t0).map { it.eventId })
        assertEquals(listOf("b"), ReminderPolicy.expired(list, t0).map { it.eventId })
        assertTrue(ReminderPolicy.label(list[0], t0).startsWith("Empezó hace 20"))
        assertEquals("Empieza en 4 min", ReminderPolicy.label(r("x", t0 + 4 * 60_000 + 30_000), t0))
    }
}
