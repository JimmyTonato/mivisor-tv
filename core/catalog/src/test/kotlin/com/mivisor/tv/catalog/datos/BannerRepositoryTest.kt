package com.mivisor.tv.catalog.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class BannerRepositoryTest {
    private class FakeGitHub : Fetcher {
        val files = HashMap<String, ByteArray>()
        val etags = HashMap<String, String>()
        val calls = ArrayList<String>()
        override fun get(url: String, etag: String?, maxBytes: Int): Fetched {
            calls += url + (if (etag != null) " If-None-Match" else "")
            val body = files[url] ?: return Fetched(404, null, null)
            val tag = etags[url] ?: "\"${body.contentHashCode()}\""
            if (etag == tag) return Fetched(304, null, null)
            return Fetched(200, body, tag)
        }
    }

    private val base = "https://raw.githubusercontent.com/u/r/banners"
    private val man = "$base/manifest.json"

    @Test fun descargaUnaVezYSoloVuelveSiCambia() {
        val gh = FakeGitHub()
        gh.files[man] = """["banners/futbol/futbol-champions-league.webp","banners/futbol/futbol-ligue-1.webp"]""".toByteArray()
        gh.files["$base/banners/futbol/futbol-champions-league.webp"] = byteArrayOf(1, 2, 3)
        var now = 1_000L
        val dir = Files.createTempDirectory("bn").toFile()
        val repo = BannerRepository(dir, gh, { now })
        val cat = repo.refresh(man)
        val ucl = cat.find("futbol", "Champions League")!!
        assertNotNull(repo.ensure(ucl, base))
        assertNull(repo.ensure(cat.find("futbol", "Ligue 1")!!, base))           // 404: no existe
        val before = gh.calls.size
        // Misma sesión y otra «sesión» (repositorio nuevo sobre la misma carpeta): no se vuelve a pedir nada.
        repo.ensure(ucl, base); repo.ensure(cat.find("futbol", "Ligue 1")!!, base)
        val again = BannerRepository(dir, gh, { now })
        val cat2 = again.loadCached()!!
        again.refresh(man)                                                          // dentro del TTL: sin red
        again.ensure(cat2.find("futbol", "Champions League")!!, base)
        assertEquals(before, gh.calls.size)
        // Cambia el manifest (sube la Ligue 1): se pide de nuevo; la Champions se revalida con 304 (no baja nada).
        gh.files["$base/banners/futbol/futbol-ligue-1.webp"] = byteArrayOf(9)
        gh.files[man] = """["banners/futbol/futbol-champions-league.webp","banners/futbol/futbol-ligue-1.webp"," "]""".toByteArray()
        now += 7 * 3600_000L
        val c3 = again.refresh(man)
        assertNotNull(again.ensure(c3.find("futbol", "Ligue 1")!!, base))
        again.ensure(c3.find("futbol", "Champions League")!!, base)
        assertEquals("$base/banners/futbol/futbol-champions-league.webp If-None-Match", gh.calls.last())
        // Borrar: todo fuera y se vuelve a descargar.
        again.clear()
        assertNull(again.cachedFile(c3.find("futbol", "Champions League")!!))
        val c4 = again.refresh(man)
        assertNotNull(again.ensure(c4.find("futbol", "Champions League")!!, base))
    }

    @Test fun conVersionEnElManifestSoloBajaLoQueCambia() {
        val gh = FakeGitHub()
        fun manifest(v: String) { gh.files[man] = """{"banners":[{"path":"banners/motor/motor-motogp.webp","sha256":"$v"}]}""".toByteArray() }
        manifest("a"); gh.files["$base/banners/motor/motor-motogp.webp"] = byteArrayOf(1)
        var now = 0L
        val repo = BannerRepository(Files.createTempDirectory("bn").toFile(), gh, { now })
        repo.ensure(repo.refresh(man).entries.single(), base)
        manifest("a"); gh.etags[man] = "nuevo"; now += 7 * 3600_000L
        val n = gh.calls.size
        repo.ensure(repo.refresh(man).entries.single(), base)                       // manifest distinto, misma versión
        assertEquals(n + 1, gh.calls.size)                                           // solo el manifest
        manifest("b"); gh.etags.remove(man); now += 7 * 3600_000L
        repo.ensure(repo.refresh(man).entries.single(), base)
        assertEquals(n + 3, gh.calls.size)                                           // manifest + la imagen
    }

    @Test fun direcciones() {
        assertEquals("https://raw.githubusercontent.com/JimmyTonato/mivisor-datos/banners/manifest.json",
            GitHubData.bannersManifest("https://raw.githubusercontent.com/JimmyTonato/mivisor-datos/main/agenda.json"))
        assertEquals("https://raw.githubusercontent.com/a/b/providers/proveedores.json",
            GitHubData.providersCatalog("https://raw.githubusercontent.com/a/b/refs/heads/main/x/agenda.json"))
        assertEquals("https://raw.githubusercontent.com/JimmyTonato/mivisor-datos/providers/proveedores.json", GitHubData.providersCatalog("https://mi.servidor/agenda.json"))
    }
}
