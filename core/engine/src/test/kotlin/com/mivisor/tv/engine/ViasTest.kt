package com.mivisor.tv.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 3t · Líneas «[mivisor]» del motor (parche 0002 de Outpace) → resumen de vías. */
class ViasTest {
    private val d = EngineDiagnostics

    @Test fun leeLasTresLineas() {
        var v = EngineDiagnostics.Vias()
        v = d.parseVia(v, "outpace: [mivisor] trackers del descriptor: 3 (udp=1 http=2 otros=0) t1:6969 t2:80 t3")
        v = d.parseVia(v, "outpace: [mivisor] pares por tracker: udp=0 http=4 (de 3 trackers)")
        v = d.parseVia(v, "outpace: [mivisor] pares por dht: 0")
        assertEquals("3 (udp=1 http=2 otros=0) t1:6969 t2:80 t3", v.trackers)
        assertEquals("udp=0 http=4 (de 3 trackers)", v.porTracker)
        assertEquals("0", v.porDht)
    }

    @Test fun ignoraLoDemas() {
        val v = d.parseVia(EngineDiagnostics.Vias(), "outpace: inbound seeding ENABLED on 0.0.0.0:18621")
        assertNull(v.trackers); assertNull(v.porTracker); assertNull(v.porDht)
    }

    @Test fun leeElDescriptor() {
        var v = EngineDiagnostics.Vias()
        v = d.parseVia(v, "outpace: [mivisor] catalogo 77.120.105.88:8081 sin respuesta")
        v = d.parseVia(v, "outpace: [mivisor] descriptor del catalogo 51.158.152.5:8081")
        v = d.parseVia(v, "outpace: [mivisor] descriptor: infohash=ab12 firmado=si pieza=1048576 titulo=Canal 1 HD")
        v = d.parseVia(v, "outpace: [mivisor] descriptor trackers: udp://t1:6969/announce http://t2/announce")
        assertEquals("51.158.152.5:8081", v.catalogo)
        assertEquals(listOf("77.120.105.88:8081 sin respuesta"), v.catalogoFallos)   // 4a: se conservan
        assertEquals("infohash=ab12 firmado=si pieza=1048576 titulo=Canal 1 HD", v.descriptor)
        assertEquals(listOf("udp://t1:6969/announce", "http://t2/announce"), v.descriptorTrackers)
        assertEquals(emptyList<String>(), d.parseVia(v, "outpace: [mivisor] descriptor trackers: ninguno").descriptorTrackers)
    }

    @Test fun leeLosCamposDelDescriptor() {
        val v = d.parseVia(EngineDiagnostics.Vias(),
            "12:00:00.000 outpace: [mivisor] descriptor claves: authmethod:texto name:texto trackers:lista")
        assertEquals("authmethod:texto name:texto trackers:lista", v.claves)
        assertEquals(null, v.descriptor)                          // «descriptor claves» no pisa «descriptor:»
    }

    @Test fun cuentaLosIntentosDeConexionYSeReiniciaConOtroCanal() {
        var v = EngineDiagnostics.Vias()
        listOf("tcp", "tcp", "handshake", "total_timeout", "connected", "rara").forEach {
            v = d.parseVia(v, "22:37:34.168 [ace] upstream attempt: result=$it phase=connect connect_ms=3004")
        }
        assertEquals("1 conectados · 2 no contestan · 1 tardan demasiado · 1 cuelgan al saludar · 1 rara", v.intentosTexto())
        v = d.parseVia(v, "outpace: [mivisor] descriptor: infohash=ab firmado=si pieza=1 titulo=Otro")
        assertEquals(null, v.intentosTexto())
    }

    @Test fun leeRouterYAlcanzabilidad() {
        var v = EngineDiagnostics.Vias()
        v = d.parseVia(v, "[portmap] no backend could map the inbound port; continuing NAT-bound")
        assertEquals("no abrió el puerto (UPnP/NAT-PMP desactivado o no disponible)", v.router)
        v = d.parseVia(v, "[portmap] mapped external 81.2.3.4:18621 -> local :18621 via upnp (lease 3600s)")
        assertEquals("abrió el puerto (81.2.3.4:18621, upnp)", v.router)
        v = d.parseVia(v, "[reachability] externally reachable: yes (2 inbound peer(s), external 81.2.3.4:18621)")
        assertEquals("sí · 2 conexiones entrantes", v.desdeFuera)
        v = d.parseVia(v, "[reachability] externally reachable: no (0 inbound peer(s), no yourip observed yet)")
        assertEquals("no · 0 conexiones entrantes", v.desdeFuera)
    }

    @Test fun emisionesPorServidorYSeReiniciaEnLaSiguienteResolucion() {
        var v = EngineDiagnostics.Vias()
        listOf(
            "outpace: [mivisor] catalogo 5.252.161.191:8081 da emision fe6ba45818b7b142",
            "outpace: [mivisor] catalogo 51.158.152.5:8081 da emision 0dca83a7cc918489",
            "outpace: [mivisor] catalogo 77.120.105.88:8081 sin respuesta",
            "outpace: [mivisor] emision fe6ba45818b7b142 tiene 3 personas en sus trackers",
            "outpace: [mivisor] emision 0dca83a7cc918489 tiene 10 personas en sus trackers",
            "outpace: [mivisor] descriptor del catalogo 51.158.152.5:8081",
            "outpace: [mivisor] descriptor: infohash=0dca firmado=si pieza=1 titulo=ESPN"
        ).forEach { v = d.parseVia(v, it) }
        assertEquals(listOf("5.252.161.191:8081 → fe6ba458 (3 personas)", "51.158.152.5:8081 → 0dca83a7 (10 personas)"),
            v.emisionesTexto())
        assertEquals(listOf("77.120.105.88:8081 sin respuesta"), v.catalogoFallos)
        v = d.parseVia(v, "outpace: [mivisor] catalogo 5.252.161.191:8081 da emision aaaa")
        assertEquals(listOf("5.252.161.191:8081 → aaaa"), v.emisionesTexto())
        assertEquals(emptyList<String>(), v.catalogoFallos)
    }
}
