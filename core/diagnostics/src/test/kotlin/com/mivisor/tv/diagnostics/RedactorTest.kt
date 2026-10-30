package com.mivisor.tv.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    private val cid = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2f9e0"

    @Test fun contentIdQuedaRecortado() {
        val out = Redactor().redact("fuente $cid sin datos")
        assertEquals("fuente a1b2…f9e0 sin datos", out)
    }

    @Test fun enlacesPrivadosSoloEsquema() {
        val r = Redactor()
        assertEquals("abrir acestream://[…]", r.redact("abrir acestream://$cid"))
        assertEquals("magnet:[…] x", r.redact("magnet:?xt=urn:btih:$cid&dn=x x"))
        assertTrue(r.redact("https://example.org/path?a=1").startsWith("https://[…]"))
    }

    @Test fun motorLocalSeConservaConIdRecortado() {
        val out = Redactor().redact("http://127.0.0.1:16900/streams/ace/cid:$cid.m3u8")
        assertTrue(out, out.contains("127.0.0.1:16900") && out.contains("a1b2…f9e0"))
    }

    @Test fun tokensYClaves() {
        val r = Redactor()
        assertFalse(r.redact("token=abc123XYZ&x=1").contains("abc123XYZ"))
        assertFalse(r.redact("{\"password\": \"hunter22\"}").contains("hunter22"))
        assertFalse(r.redact("Authorization: Bearer eyJabc.def").contains("eyJabc"))
    }

    @Test fun ips() {
        val r = Redactor()
        assertEquals("emisor [ip] y casa 192.168.x.x", r.redact("emisor 83.44.12.9 y casa 192.168.1.40"))
    }

    @Test fun codigosDeProveedor() {
        val r = Redactor(); r.addSecret("7gx2kq94"); r.addSecret("7gx2kq94.proveedor.example")
        val out = r.redact("leyendo 7gx2kq94.proveedor.example (7GX2KQ94)")
        assertFalse(out, out.lowercase().contains("7gx2kq94"))
    }
}
