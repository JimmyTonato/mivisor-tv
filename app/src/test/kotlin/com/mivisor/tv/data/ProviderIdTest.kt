package com.mivisor.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderIdTest {
    @Test fun elIdEsEstableYNoContieneElCodigo() {
        val id = ProvidersRepository.idOf("codigo-de-prueba")
        assertEquals(id, ProvidersRepository.idOf("codigo-de-prueba"))
        assertEquals(16, id.length)
        assertFalse(id.contains("codigo"))
        assertTrue(ProvidersRepository.friendly(java.io.IOException("x")).isNotBlank())
    }
}
