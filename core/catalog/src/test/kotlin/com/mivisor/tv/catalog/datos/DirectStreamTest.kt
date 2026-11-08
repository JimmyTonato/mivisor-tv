package com.mivisor.tv.catalog.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectStreamTest {
    private val id = "0123456789abcdef0123456789abcdef01234567"

    @Test fun aceptaLoQueSeSueleCopiar() {
        assertEquals(id, DirectStream.parse(id))
        assertEquals(id, DirectStream.parse("acestream://$id"))
        assertEquals(id, DirectStream.parse("  ACESTREAM://${id.uppercase()}  "))
        assertEquals(id, DirectStream.parse("http://127.0.0.1:6878/ace/getstream?id=$id&pid=1"))
        assertEquals(id, DirectStream.parse("https://x.y/play?content_id=$id"))
        assertEquals(id, DirectStream.parse("mira este 👉 acestream://$id 👍"))
    }

    @Test fun rechazaLoQueNoEsUnId() {
        assertNull(DirectStream.parse(""))
        assertNull(DirectStream.parse("6a9fd362fa2693a3a8f5dcfd"))            // un código de proveedor (24): no
        assertNull(DirectStream.parse(id + "ab"))                             // 42 seguidos: no es un ID
        assertNull(DirectStream.parse("hola"))
    }

    @Test fun idDeCanal() {
        assertEquals(id, DirectStream.contentIdOf(DirectStream.channelId(id)))
        assertNull(DirectStream.contentIdOf("dazn-laliga"))
        assertEquals("…234567", DirectStream.short(id))
    }
}
