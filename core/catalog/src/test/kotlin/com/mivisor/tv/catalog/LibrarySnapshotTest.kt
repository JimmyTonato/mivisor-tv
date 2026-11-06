package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.Picons
import com.mivisor.tv.catalog.tv.LibrarySnapshot
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.Catalog
import com.mivisor.tv.model.Channel
import com.mivisor.tv.model.ChannelKind
import com.mivisor.tv.model.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 3i · La biblioteca guardada en disco se lee IGUAL que se montó. */
class LibrarySnapshotTest {
    private fun ch(id: Int, name: String, url: String = "acestream://" + "%040x".format(id)) = Channel("c$id", name, url, null, ChannelKind.ACE, "", 0)

    private fun dump(lib: TvLibrary) = buildString {
        append("P${lib.providerCount}\n")
        lib.channels.forEach { c ->
            append("${c.id}|${c.name}|${c.brandKey}|${c.brandName}|${c.initials}|${c.logo}|${c.category}|${c.family}\n")
            c.sources.forEach { s -> append("  ${s.contentId}|${s.providerId}|${s.providerName}|${s.quality}|${s.qualityLabel}|${s.variantName}\n") }
        }
        lib.sections.forEach { s -> append("S ${s.key}|${s.name}|${s.logo}|${s.channels.map { it.id }}\n") }
        lib.providers.forEach { s -> append("PR ${s.key}|${s.channels.size}\n") }
        lib.links.forEach { l -> append("L ${l.id}|${l.name}|${l.url}|${l.kind}|${l.providerName}\n") }
    }

    @Test fun idaYVueltaIdentica() {
        val a = listOf(ch(1, "Movistar LaLiga 2 FHD"), ch(2, "M+ LaLiga 2 HD"), ch(3, "DAZN 1"), ch(4, "Eurosport 1"), ch(5, "Canal Raro ñ «x»"))
        val b = listOf(ch(6, "DAZN 1 1080"), ch(7, "M+ Liga de Campeones"), ch(8, "Teledeporte"))
        val link = Channel("l1", "Partido por Telegram", "tg://resolve?domain=x", null, ChannelKind.TELEGRAM, "", 0)
        val provs = listOf(
            ProviderCatalog("p1", "Prov 1", Catalog(listOf(Section("s", "S", a)), a.size)),
            ProviderCatalog("p2", "Prov 2", Catalog(listOf(Section("s", "S", b)), b.size), links = listOf(link))
        )
        val lib = TvLibrary.build(provs, ChannelLogoResolver(LogoCatalog.EMPTY, Picons.entries(listOf("M+ LaLiga 2.png", "DAZN 1.png"))))
        val file = File(Files.createTempDirectory("snap").toFile(), "library-snapshot.json")
        val print = TvLibrary.fingerprint(provs)
        LibrarySnapshot.write(file, lib, print)
        val back = LibrarySnapshot.read(file)!!
        assertEquals(print, back.fingerprint)
        assertEquals(dump(lib), dump(back.library))
        assertEquals(lib.channel("movistar laliga 2")?.sources?.size, back.library.channel("movistar laliga 2")?.sources?.size)
    }

    @Test fun archivoRotoOAusenteSeIgnora() {
        val dir = Files.createTempDirectory("snap2").toFile()
        assertNull(LibrarySnapshot.read(File(dir, "no-existe.json")))
        val broken = File(dir, "roto.json").also { it.writeText("{\"v\":1,\"print\":5,\"channels\":[{\"id\":\"x\",") }
        assertNull(LibrarySnapshot.read(broken))
        val other = File(dir, "otra.json").also { it.writeText("{\"v\":99,\"print\":5,\"channels\":[]}") }
        assertNull(LibrarySnapshot.read(other))
    }
}
