package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.LogoEntry
import com.mivisor.tv.catalog.logos.LogoSource
import com.mivisor.tv.catalog.logos.Picons
import org.junit.Assert.assertEquals
import org.junit.Test

class PiconsTest {
    @Test fun urlIsEncodedLikeGitHubRaw() {
        assertEquals("https://raw.githubusercontent.com/davidmuma/picons_dobleM/master/icon/M%2B%20LaLiga.png", Picons.url("M+ LaLiga.png"))
    }

    @Test fun parsesTrees() {
        val root = """{"tree":[{"path":"blanco","type":"tree","sha":"a"},{"path":"icon","type":"tree","sha":"abc123"}]}"""
        assertEquals("abc123", Picons.iconTreeSha(root))
        val tree = """{"tree":[{"path":"M+ LaLiga.png","type":"blob"},{"path":"x/y.png","type":"blob"},{"path":"leeme.txt","type":"blob"}]}"""
        assertEquals(listOf("M+ LaLiga.png"), Picons.pngNames(tree))
    }

    @Test fun piconsWinAndMatchNormalizedNames() {
        val names = listOf("M+ LaLiga.png", "M+ LaLiga 2.png", "DAZN 1.png", "DAZN LaLiga.png")
        val other = LogoEntry("DAZN 1", "https://example.com/otro.png", LogoSource.MANUAL)
        val r = ChannelLogoResolver(LogoCatalog.EMPTY, Picons.entries(names) + other)
        assertEquals(Picons.url("M+ LaLiga 2.png"), r.resolve("Movistar LaLiga 2 FHD").channelLogo)
        assertEquals(Picons.url("M+ LaLiga.png"), r.resolve("M+ LALIGA HD").channelLogo)
        assertEquals(Picons.url("DAZN 1.png"), r.resolve("DAZN 1").channelLogo)
    }
}
