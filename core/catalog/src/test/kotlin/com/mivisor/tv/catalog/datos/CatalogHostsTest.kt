package com.mivisor.tv.catalog.datos

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogHostsTest {
    @Test fun leeLaListaEnOrdenYDescartaLoRaro() {
        val p = CatalogHosts.parse("""{"schema_version":1,"updated":"2026-11-08","hosts":[
            {"host":"51.158.152.5","port":8081,"note":"nuevo"},
            {"host":"5.252.161.191"},
            {"host":"51.158.152.5","port":8081},
            {"host":"192.168.1.10","port":8081},
            {"host":"catalog.example.com","port":8081},
            {"host":"77.120.105.88","port":99999},
            {"host":"64.227.119.64","port":8081,"enabled":false}]}""")
        assertEquals(listOf("51.158.152.5:8081", "5.252.161.191:8081"), p.hosts)
        assertEquals(4, p.skipped)
        assertEquals("2026-11-08", p.updated)
    }

    @Test(expected = IllegalArgumentException::class) fun sinServidoresValidosNoSeAcepta() {
        CatalogHosts.parse("""{"schema_version":1,"hosts":[{"host":"10.0.0.1","port":8081}]}""")
    }

    @Test fun direccion() {
        assertEquals("https://raw.githubusercontent.com/JimmyTonato/mivisor-datos/catalogo/catalog-hosts.json",
            GitHubData.catalogHosts("https://raw.githubusercontent.com/JimmyTonato/mivisor-datos/main/agenda.json"))
    }
}
