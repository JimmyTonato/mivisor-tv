package com.mivisor.tv.catalog.datos

import com.mivisor.tv.catalog.datos.CentralProviders.Local
import com.mivisor.tv.catalog.datos.CentralProviders.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CentralProvidersTest {
    private val idOf: (String) -> String = { "id:$it" }

    @Test fun leeElCatalogoReal() {
        val p = CentralProviders.parse("""{"schema_version":1,"providers":[
            {"id":"6a9fd362fa2693a3a8f5dcfd","name":"EUPHORBIA CHAMPIONS"},
            {"id":"6A9FD362FA2693A3A8F5DCFD","name":"Repetido en mayúsculas"},
            {"id":1001,"name":"Numérico"},
            {"id":"","name":"Sin código"},
            {"id":"x1","name":"  "},
            {"id":"694a9cd6d05ee80b5cd7eb19","name":"ORDÓÑEZ","url":"ignorado"}]}""")
        assertEquals(listOf("6a9fd362fa2693a3a8f5dcfd", "1001", "694a9cd6d05ee80b5cd7eb19"), p.providers.map { it.code })
        assertEquals("ORDÓÑEZ", p.providers[2].name)
        assertEquals(3, p.skipped)
    }

    @Test(expected = InvalidProviderCatalogException::class) fun rechazaSinLista() { CentralProviders.parse("""{"schema_version":1}""") }
    @Test(expected = InvalidProviderCatalogException::class) fun rechazaVersionFutura() { CentralProviders.parse("""{"schema_version":2,"providers":[]}""") }
    @Test(expected = InvalidProviderCatalogException::class) fun rechazaBasura() { CentralProviders.parse("<html>") }

    private val remote = listOf(CentralProviders.Remote("1001", "Proveedor A"), CentralProviders.Remote("1002", "Proveedor B"), CentralProviders.Remote("1003", "Proveedor C"))

    @Test fun primeraVezLosAnadeTodos() {
        val plan = CentralProviders.plan(remote, emptyList(), Mode.FULL, emptySet(), idOf)
        assertEquals(listOf("1001", "1002", "1003"), plan.add.map { it.code })
        assertEquals(setOf("id:1001", "id:1002", "id:1003"), plan.github)
    }

    @Test fun sinPerderLosMios() {
        val local = listOf(Local("id:1001", "Proveedor A", true), Local("id:1002", "Viejo nombre", true), Local("id:9000", "Mi proveedor personal", false),
            Local("id:1004", "Quitado de GitHub", true))
        val keep = CentralProviders.plan(remote, local, Mode.KEEP_MINE, emptySet(), idOf)
        assertEquals(listOf("1003"), keep.add.map { it.code })
        assertEquals(mapOf("id:1002" to "Proveedor B"), keep.rename)
        assertTrue(keep.remove.isEmpty())                                    // no quita nada
        val full = CentralProviders.plan(remote, local, Mode.FULL, emptySet(), idOf)
        assertEquals(setOf("id:1004"), full.remove)                          // solo los de GitHub que ya no están
        assertTrue("id:9000" !in full.remove)                                // el personal nunca
    }

    @Test fun personalConCodigoCentralNoSeDuplica() {
        val local = listOf(Local("id:1002", "Lo añadí yo", false))
        val plan = CentralProviders.plan(remote, local, Mode.KEEP_MINE, emptySet(), idOf)
        assertEquals(listOf("1001", "1003"), plan.add.map { it.code })
        assertEquals(setOf("id:1002"), plan.adopt)
        assertEquals("Proveedor B", plan.rename["id:1002"])                  // manda el central
    }

    @Test fun alIniciarRespetaLosQueBorraste() {
        val local = listOf(Local("id:1001", "Proveedor A", true))
        val auto = CentralProviders.plan(remote, local, Mode.AUTO, setOf("id:1002"), idOf)
        assertEquals(listOf("1003"), auto.add.map { it.code })
        assertEquals(setOf("id:1002"), auto.dismissed)
        val manual = CentralProviders.plan(remote, local, Mode.FULL, setOf("id:1002"), idOf)
        assertEquals(listOf("1002", "1003"), manual.add.map { it.code })
        assertTrue(manual.dismissed.isEmpty())
    }
}
