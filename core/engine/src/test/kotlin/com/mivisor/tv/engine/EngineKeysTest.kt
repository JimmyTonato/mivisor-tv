package com.mivisor.tv.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4f · Abrir con el descriptor publicado por MiVisor Gold (tf-…) y volver a cid: si falla. */
class EngineKeysTest {
    private val cid = "A".repeat(40)
    private val b64 = "QWNlU3RyZWFtVHJhbnNwb3J0+/8AAQIDBAUG=="

    @After fun clean() = EngineKeys.clear()

    @Test fun sinDescriptorEsCid() {
        assertEquals("cid:" + "a".repeat(40), EngineApi.key(cid))
    }

    @Test fun conDescriptorEsTfEnBase64Url() {
        EngineKeys.replaceAll(mapOf(cid to b64))
        assertEquals("tf-QWNlU3RyZWFtVHJhbnNwb3J0-_8AAQIDBAUG", EngineApi.key(cid.lowercase()))
        assertTrue(EngineKeys.usingInline(cid))
    }

    @Test fun siFallaVuelveACidHastaQueLlegueOtroDescriptor() {
        EngineKeys.replaceAll(mapOf(cid to b64))
        assertTrue(EngineKeys.dropInline(cid))
        assertFalse(EngineKeys.dropInline(cid))
        assertEquals("cid:" + "a".repeat(40), EngineApi.key(cid))
        EngineKeys.replaceAll(mapOf(cid to b64))          // mismo descriptor: sigue en cid:
        assertFalse(EngineKeys.usingInline(cid))
        EngineKeys.replaceAll(emptyMap())                  // se quita y vuelve…
        EngineKeys.replaceAll(mapOf(cid to b64 + "AAAA"))  // …con otro: nueva oportunidad
        assertTrue(EngineKeys.usingInline(cid))
    }

    @Test fun descriptoresRarosNoSeUsan() {
        assertNull(EngineKeys.toInlineKey("corto"))
        assertNull(EngineKeys.toInlineKey("con espacios raros ### y símbolos ¿?"))
        assertNull(EngineKeys.toInlineKey("A".repeat(EngineKeys.MAX_INLINE_CHARS + 4)))
        EngineKeys.replaceAll(mapOf(cid to "###"))
        assertEquals("cid:" + "a".repeat(40), EngineApi.key(cid))
    }
}

/** 4f · Huella del descriptor que da el catálogo (parche 0011), para compararla con la publicada por Gold. */
class CatalogDescriptorSha1Test {
    @Test fun seLeeSoloLaDelCatalogo() {
        EngineDiagnostics.resetCatalogDescriptor()
        EngineDiagnostics.engineLine("outpace: [mivisor] descriptor sha1=${"a".repeat(40)} origen=publicado")
        assertNull(EngineDiagnostics.lastCatalogDescriptorSha1)
        EngineDiagnostics.engineLine("outpace: [mivisor] descriptor sha1=${"B".repeat(40)} origen=catalogo")
        assertEquals("b".repeat(40), EngineDiagnostics.lastCatalogDescriptorSha1)
        EngineDiagnostics.resetCatalogDescriptor()
        assertNull(EngineDiagnostics.lastCatalogDescriptorSha1)
    }
}
