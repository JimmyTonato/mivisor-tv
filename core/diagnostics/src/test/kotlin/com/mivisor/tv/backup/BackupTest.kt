package com.mivisor.tv.backup

import com.mivisor.tv.diagnostics.DiagnosticReport
import com.mivisor.tv.diagnostics.Redactor
import com.mivisor.tv.diagnostics.ReportFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val data = BackupData(
        createdAt = 1L,
        providers = listOf(BackupProvider("Casa", "codigo-secreto", true)),
        favorites = listOf(BackupFavorite("dazn laliga", "DAZN LaLiga")),
        reminders = listOf(BackupReminder("e1", "Betis – Sevilla", 2L, "futbol", "LaLiga", listOf("DAZN LaLiga"))),
        settings = mapOf("lite" to "true")
    )

    @Test fun cifraYDescifraConElPin() {
        val pin = BackupCodec.newPin()
        assertEquals(6, pin.length)
        val bytes = BackupCodec.encrypt(data, pin, iterations = 2_000)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("codigo-secreto"))
        val r = BackupCodec.decrypt(bytes, pin)
        assertTrue(r is BackupCodec.Result.Ok)
        assertEquals(data, (r as BackupCodec.Result.Ok).data)
        assertEquals("1 proveedor, 1 favorito, 1 recordatorio y tus ajustes", r.data.summary())
    }

    @Test fun pinEquivocadoOFicheroAjeno() {
        val bytes = BackupCodec.encrypt(data, "123456", iterations = 2_000)
        assertTrue(BackupCodec.decrypt(bytes, "654321") is BackupCodec.Result.WrongPin)
        assertTrue(BackupCodec.decrypt("hola".toByteArray(), "123456") is BackupCodec.Result.NotABackup)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        assertTrue(BackupCodec.decrypt(bytes, "123456") is BackupCodec.Result.WrongPin)
    }

    @Test fun informeLimpioYPorPartes() {
        val r = Redactor().apply { addSecret("codigo-secreto") }
        val f = ReportFacts("MiVisor TV 1.0", "30/10/2026 21:34", "Android TV 9", "Wi-Fi · IP 85.1.2.3", "Outpace en marcha",
            "No instalada", "Conectada", "1 · codigo-secreto", "al día", 2, "412 MB", "2,1 GB")
        val text = DiagnosticReport.build(f, listOf("21:30  Fuente  acestream://" + "a".repeat(40)), r)
        assertFalse(text.contains("codigo-secreto"))
        assertFalse(text.contains("85.1.2.3"))
        assertFalse(text.contains("a".repeat(40)))
        val parts = DiagnosticReport.parts("x".repeat(1500), 700)
        assertEquals(3, parts.size)
        assertTrue(parts[2].startsWith("MiVisor TV · parte 3/3"))
    }
}
