package com.mivisor.tv.catalog.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BannerCatalogTest {
    /** Los ficheros reales de la rama banners (6-nov-2026). */
    private val real = listOf(
        "banners/combate/combate-ufc-fight-night.webp", "banners/combate/combate-ufc.webp",
        "banners/futbol/futbol-bundesliga.webp", "banners/futbol/futbol-champions-league.webp", "banners/futbol/futbol-conference-league.webp",
        "banners/futbol/futbol-copa-del-rey.webp", "banners/futbol/futbol-europa-league.webp", "banners/futbol/futbol-laliga-ea-sports.webp",
        "banners/futbol/futbol-laliga-hypermotion.webp", "banners/futbol/futbol-ligue-1.webp", "banners/futbol/futbol-nations-league.webp",
        "banners/futbol/futbol-premier-league.webp", "banners/futbol/futbol-serie-a-italia.webp", "banners/futbol/futbol-supercopa-de-espana.webp",
        "banners/motor/motor-formula-1.webp", "banners/motor/motor-motogp.webp",
        "banners/tenis/tenis-atp-finals.webp", "banners/tenis/tenis-australian-open.webp", "banners/tenis/tenis-roland-garros.webp",
        "banners/tenis/tenis-us-open-tennis.webp", "banners/tenis/tenis-wimbledon.webp"
    )
    private val cat = BannerCatalog.fromPaths(real.associateWith { null })
    private fun f(sport: String, comp: String?) = cat.find(sport, comp)?.slug

    @Test fun emparejaLasCompeticionesReales() {
        assertEquals("champions-league", f("futbol", "UEFA Champions League"))
        assertEquals("champions-league", f("futbol", "Champions League"))
        assertEquals("champions-league", f("futbol", "Liga de Campeones"))
        assertEquals("laliga-ea-sports", f("futbol", "LaLiga EA Sports"))
        assertEquals("laliga-ea-sports", f("futbol", "LaLiga"))
        assertEquals("laliga-ea-sports", f("futbol", "Primera División"))
        assertEquals("laliga-hypermotion", f("futbol", "LaLiga Hypermotion"))
        assertEquals("serie-a-italia", f("futbol", "Serie A"))
        assertEquals("supercopa-de-espana", f("futbol", "Supercopa de España"))
        assertEquals("copa-del-rey", f("futbol", "Copa del Rey"))
        assertEquals("europa-league", f("futbol", "UEFA Europa League"))
        assertEquals("conference-league", f("futbol", "UEFA Conference League"))
        assertEquals("nations-league", f("futbol", "UEFA Nations League"))
        assertEquals("premier-league", f("futbol", "Premier League"))
        assertEquals("ligue-1", f("futbol", "Ligue 1"))
        assertEquals("bundesliga", f("futbol", "Bundesliga"))
        assertEquals("formula-1", f("formula1", null))
        assertEquals("formula-1", f("formula1", "Fórmula 1 · GP de Abu Dabi"))
        assertEquals("motogp", f("motogp", "MotoGP"))
        assertEquals("ufc", f("ufc", "UFC 310"))
        assertEquals("ufc-fight-night", f("ufc", "UFC Fight Night"))
        assertEquals("atp-finals", f("tenis", "Nitto ATP Finals"))
        assertEquals("us-open-tennis", f("tenis", "US Open"))
        assertEquals("australian-open", f("tenis", "Abierto de Australia"))
        assertEquals("roland-garros", f("tenis", "Roland Garros"))
        assertEquals("wimbledon", f("tenis", "Wimbledon"))
    }

    @Test fun noPoneElBannerDeOtraCompeticion() {
        assertNull(f("futbol", "AFC Champions League"))
        assertNull(f("futbol", "UEFA Women's Champions League"))
        assertNull(f("futbol", "UEFA Youth League"))
        assertNull(f("futbol", "Copa Libertadores"))
        assertNull(f("futbol", "Premier League 2"))
        assertNull(f("futbol", "LaLiga F"))
        assertNull(f("tenis", "WTA Finals"))
        assertNull(f("baloncesto", "Euroliga"))                       // sin carpeta de baloncesto: fondo de siempre
        assertNull(f("tenis", "Champions League"))                    // solo banners de su deporte
    }

    @Test fun genericoDelDeporte() {
        val c = BannerCatalog.fromPaths(mapOf("banners/futbol/futbol.webp" to null, "banners/futbol/futbol-premier-league.webp" to null))
        assertEquals("", c.find("futbol", "Eredivisie")?.slug)
        assertEquals("premier-league", c.find("futbol", "Premier League")?.slug)
    }

    @Test fun manifestFlexible() {
        val a = BannerCatalog.parse("""{"version":1,"banners":[{"path":"banners/futbol/futbol-ligue-1.webp","sha256":"abc","size":123}]}""")
        assertEquals("banners/futbol/futbol-ligue-1.webp", a.entries.single().path)
        assertEquals("sha256=abc|size=123", a.entries.single().version)
        val b = BannerCatalog.parse("""["banners/motor/motor-motogp.webp","futbol/futbol-bundesliga.webp"]""")
        assertEquals(listOf("banners/motor/motor-motogp.webp", "banners/futbol/futbol-bundesliga.webp"), b.entries.map { it.path })
        val c = BannerCatalog.parse("""{"files":{"banners/tenis/tenis-wimbledon.webp":{"sha":"9f","bytes":10}}}""")
        assertEquals("sha=9f|bytes=10", c.entries.single().version)
        assertEquals("tenis", c.entries.single().folder)
        val d = BannerCatalog.parse("""{"futbol":{"champions-league":"banners/futbol/futbol-champions-league.webp"}}""")
        assertEquals("champions-league", d.entries.single().slug)
    }
}
