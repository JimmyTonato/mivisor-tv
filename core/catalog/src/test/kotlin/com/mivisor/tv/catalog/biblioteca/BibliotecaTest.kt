package com.mivisor.tv.catalog.biblioteca

import com.mivisor.tv.catalog.tv.GoldLibrary
import com.mivisor.tv.catalog.tv.TvLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

/** 4f · Biblioteca de MiVisor Gold: descifrado, formato, cambios incrementales y orden. */
class BibliotecaTest {
    private val pass = "contraseña de prueba".toCharArray()
    private val iters = 1_000

    private fun gz(text: String): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray()
    private fun enc(text: String, p: CharArray = pass, salt: Byte = 1) =
        BibliotecaCrypto.encrypt(gz(text), p, iters, ByteArray(16) { salt }, ByteArray(12) { 7 })

    private val A = "a".repeat(40)
    private val B = "b".repeat(40)
    private val C = "c".repeat(40)

    private fun canal(id: String, name: String, orden: Int, cat: String, vararg ids: String) =
        """{"tipo":"canal","channel_id":"$id","name":"$name","category_id":"$cat","orden":$orden,"ids":[${ids.joinToString(",")}]}"""

    private fun idJson(asset: String, banda: String = "verde", extra: String = "") =
        """{"asset_id":"$asset","verificacion":{"nota":88,"banda":"$banda","primera_pieza_s":3.5,"avisos":[],"info":["formato_exigente"],""" +
            """"calidad":{"etiqueta":"Full HD · H.264 · 6,2 Mb/s · AAC estéreo","clase":"Full HD","audios":[{"codec":"AAC","idioma":"spa","canales":"estéreo"}]},""" +
            """"emision":"${"e".repeat(40)}","descriptor":{"base64":"QUJD","sha1":"x","pedido":"2026-11-14"}}$extra}"""

    private val full = listOf(
        """{"tipo":"resumen","schema":"mivisor.biblioteca/2","revision":800,"summary":{"channels":3}}""",
        """{"tipo":"categoria","id":"m","name":"Movistar Plus+","parent_id":null,"kind":"marca","orden":2}""",
        """{"tipo":"categoria","id":"d","name":"DAZN","parent_id":null,"kind":"marca","orden":1}""",
        """{"tipo":"categoria","id":"m-fut","name":"Fútbol","parent_id":"m","kind":"familia","orden":1}""",
        canal("c2", "M+ LaLiga 2", 20, "m-fut", idJson(B), idJson(A, "medio")),
        canal("c1", "M+ LaLiga", 10, "m-fut", idJson(A)),
        canal("c3", "DAZN 1", 5, "d", idJson(C), """{"asset_id":"no-es-un-id"}""")
    ).joinToString("\n")

    @Test fun descifraElFormatoMvb1() {
        val text = BibliotecaCrypto.decrypt(enc(full), pass, iters)
        assertEquals(full, text)
    }

    @Test fun contrasenaIncorrectaSeAvisaSinRomper() {
        try {
            BibliotecaCrypto.decrypt(enc(full), "otra".toCharArray(), iters)
            throw AssertionError("debía fallar")
        } catch (e: BibliotecaCrypto.WrongPasswordException) {
            assertEquals("Contraseña de la Biblioteca incorrecta", e.message)
        }
    }

    @Test fun formatoMalSeRechaza() {
        try {
            BibliotecaCrypto.decrypt("XXXX".toByteArray() + ByteArray(60), pass, iters)
            throw AssertionError("debía fallar")
        } catch (e: BibliotecaCrypto.FormatException) { /* bien */ }
    }

    @Test fun manifestDelFormato() {
        val m = Manifest.parse("""{"formato":"mivisor.biblioteca/2","revision":812,"publicado":"2026-11-14T10:00:00Z","cambios_desde":790,"cifrado":{"alg":"AES-256-GCM","kdf":"PBKDF2-HMAC-SHA256","iteraciones":200000},"totales":{"canales":194,"ids":311,"categorias":45}}""")!!
        assertEquals(812L, m.revision); assertEquals(790L, m.cambiosDesde); assertEquals(200_000, m.iteraciones); assertEquals(311, m.ids)
        assertNull(Manifest.parse("""{"formato":"otro/1","revision":1}"""))
        assertNull(Manifest.parse("no json"))
    }

    @Test fun ficheroCompletoYOrdenSinTocar() {
        val bib = BibliotecaParser.parseFull(full)
        assertEquals(800L, bib.revision)
        assertEquals(3, bib.canales.size)
        assertEquals(listOf(C), bib.canales.getValue("c3").ids.map { it.assetId })   // el ID no válido se descarta
        assertEquals(listOf(B, A), bib.canales.getValue("c2").ids.map { it.assetId }) // ids[] tal cual
        val v = bib.canales.getValue("c1").ids[0].verificacion!!
        assertEquals("verde", v.banda); assertEquals(3, v.calidad!!.rank); assertEquals("QUJD", v.descriptor!!.base64)
    }

    @Test fun bibliotecaParaLaPantalla() {
        val lib = GoldLibrary.build(BibliotecaParser.parseFull(full))
        assertTrue(lib.gold)
        assertEquals(listOf("c3", "c1", "c2"), lib.channels.map { it.id })                 // por «orden»
        assertEquals(listOf("DAZN", "Movistar Plus+"), lib.sections.map { it.name })     // marcas por «orden»
        assertEquals(listOf("c1", "c2"), lib.section("gold:m")!!.channels.map { it.id })  // la familia cuelga de la marca
        assertEquals(listOf(B, A), lib.channel("c2")!!.sources.map { it.contentId })     // sin reordenar
        val s = lib.channel("c2")!!.sources[1]
        assertEquals("medio", s.gold!!.band); assertEquals(GoldLibrary.PROVIDER_ID, s.providerId)
        assertEquals(listOf("c3", "c1", "c2"), TvLibrary.sorted(lib.channels.reversed()).map { it.id })
    }

    @Test fun huellaDelDescriptorPublicado() {
        val lib = GoldLibrary.build(BibliotecaParser.parseFull(full))
        // «QUJD» = bytes «ABC»: SHA-1 conocido.
        assertEquals("3c01bdbb26f358bab27f267924aa2c9a03fcfdb8", lib.channel("c1")!!.sources[0].gold!!.descriptorSha1)
        assertNull(GoldLibrary.sha1Of("***"))
    }

    @Test fun cambiosSoloTocanLoAfectado() {
        val base = BibliotecaParser.parseFull(full)
        val lib1 = GoldLibrary.build(base)
        val changes = BibliotecaParser.parseChanges(listOf(
            """{"rev":799,"op":"baja","tipo":"canal","id":"c1"}""",                       // vieja: se ignora
            """{"rev":801,"op":"cambio","tipo":"canal","id":"c2","datos":${canal("c2", "M+ LaLiga 2", 20, "m-fut", idJson(A))}}""",
            """{"rev":802,"op":"alta","tipo":"canal","id":"c4","datos":${canal("c4", "DAZN 2", 6, "d", idJson(B))}}""",
            """{"rev":803,"op":"baja","tipo":"canal","id":"c3"}"""
        ).joinToString("\n"))
        val out = BibliotecaParser.apply(base, changes, 803)
        assertEquals(803L, out.biblioteca.revision)
        assertEquals(setOf("c2", "c3", "c4"), out.canales)
        assertTrue("c1" in out.biblioteca.canales)
        val lib2 = GoldLibrary.build(out.biblioteca, previous = lib1, touchedChannels = out.canales, touchedCategories = out.categorias)
        assertEquals(listOf("c4", "c1", "c2"), lib2.channels.map { it.id })
        assertSame(lib1.channel("c1"), lib2.channel("c1"))       // sin cambios: el mismo objeto (no se repinta)
        assertNotSame(lib1.channel("c2"), lib2.channel("c2"))
        assertEquals(listOf(A), lib2.channel("c2")!!.sources.map { it.contentId })
    }

    @Test fun cambioDeCategoriaRehaceSusCanales() {
        val base = BibliotecaParser.parseFull(full)
        val lib1 = GoldLibrary.build(base)
        val ch = BibliotecaParser.parseChanges("""{"rev":801,"op":"cambio","tipo":"categoria","id":"m","datos":{"tipo":"categoria","id":"m","name":"M+","parent_id":null,"kind":"marca","orden":0}}""")
        val out = BibliotecaParser.apply(base, ch, 801)
        val lib2 = GoldLibrary.build(out.biblioteca, previous = lib1, touchedChannels = out.canales, touchedCategories = out.categorias)
        assertEquals(listOf("M+", "DAZN"), lib2.sections.map { it.name })
        assertNotSame(lib1.channel("c1"), lib2.channel("c1"))
        assertEquals("M+", lib2.channel("c1")!!.brandName)
        assertSame(lib1.channel("c3"), lib2.channel("c3"))
    }

    @Test fun cambioSinDatosNoDejaNadaAMedias() {
        val base = BibliotecaParser.parseFull(full)
        val ch = BibliotecaParser.parseChanges("""{"rev":801,"op":"cambio","tipo":"canal","id":"c2"}""")
        try { BibliotecaParser.apply(base, ch, 801); throw AssertionError("debía fallar") } catch (e: BibliotecaFormatException) { /* bien */ }
        assertEquals(800L, base.revision)
    }

    // ------------------------------------------------------------------ sincronización

    private class FakeGitHub(var manifest: String, var biblioteca: ByteArray?, var cambios: ByteArray?) {
        val asked = ArrayList<String>()
        fun fetch(url: String): ByteArray {
            val file = url.substringAfterLast('/').substringBefore('?')
            asked += file
            return when (file) {
                "manifest.json" -> manifest.toByteArray()
                "biblioteca.enc" -> biblioteca ?: throw java.io.IOException("404")
                "cambios.enc" -> cambios ?: throw java.io.IOException("404")
                else -> throw java.io.IOException("404")
            }
        }
    }

    private fun manifest(rev: Long, since: Long) =
        """{"formato":"mivisor.biblioteca/2","revision":$rev,"cambios_desde":$since,"cifrado":{"alg":"AES-256-GCM","iteraciones":$iters}}"""

    @Test fun sinCopiaSeBajaEntera() {
        val gh = FakeGitHub(manifest(800, 790), enc(full), null)
        val o = BibliotecaSync({ "https://raw/x/biblioteca" }, gh::fetch).sync(null, pass)
        assertTrue(o is BibliotecaSync.Outcome.Updated && o.full)
        assertEquals(listOf("manifest.json", "biblioteca.enc"), gh.asked)
    }

    @Test fun mismaRevisionNoBajaNada() {
        val gh = FakeGitHub(manifest(800, 790), enc(full), null)
        val o = BibliotecaSync({ "u" }, gh::fetch).sync(BibliotecaParser.parseFull(full), pass)
        assertTrue(o is BibliotecaSync.Outcome.Unchanged)
        assertEquals(listOf("manifest.json"), gh.asked)
    }

    @Test fun copiaRecienteUsaSoloLosCambios() {
        val cambios = enc("""{"rev":801,"op":"baja","tipo":"canal","id":"c3"}""" + "\n" + """{"rev":802,"op":"baja","tipo":"canal","id":"c1"}""")
        val gh = FakeGitHub(manifest(802, 795), null, cambios)
        val o = BibliotecaSync({ "u" }, gh::fetch).sync(BibliotecaParser.parseFull(full), pass)
        assertTrue(o is BibliotecaSync.Outcome.Updated && !o.full)
        val r = (o as BibliotecaSync.Outcome.Updated).result
        assertEquals(802L, r.biblioteca.revision); assertEquals(setOf("c2"), r.biblioteca.canales.keys)
        assertEquals(listOf("manifest.json", "cambios.enc"), gh.asked)
    }

    @Test fun copiaMuyViejaBajaEntera() {
        val gh = FakeGitHub(manifest(800, 799), enc(full), enc(""))
        val old = BibliotecaParser.parseFull(full.replace("\"revision\":800", "\"revision\":700"))
        val o = BibliotecaSync({ "u" }, gh::fetch).sync(old, pass)
        assertTrue(o is BibliotecaSync.Outcome.Updated && o.full)
        assertFalse("cambios.enc" in gh.asked)
    }

    @Test fun cacheDeGitHubViejaNoSeGuarda() {
        // El manifest ya dice 805, pero cambios.enc y biblioteca.enc siguen siendo de la 800.
        val gh = FakeGitHub(manifest(805, 790), enc(full), enc("""{"rev":800,"op":"baja","tipo":"canal","id":"c3"}"""))
        val o = BibliotecaSync({ "u" }, gh::fetch).sync(BibliotecaParser.parseFull(full.replace("\"revision\":800", "\"revision\":799")), pass)
        assertTrue(o is BibliotecaSync.Outcome.NotReady)
    }

    @Test fun contrasenaMalEnLaSincronizacion() {
        val gh = FakeGitHub(manifest(800, 790), enc(full, "otra".toCharArray(), salt = 9), null)
        assertTrue(BibliotecaSync({ "u" }, gh::fetch).sync(null, pass) is BibliotecaSync.Outcome.WrongPassword)
    }

    @Test fun guardarYLeerConLaRevisionAplicada() {
        val dir = Files.createTempDirectory("bib").toFile()
        val store = BibliotecaStore(dir)
        val base = BibliotecaParser.parseFull(full)
        val out = BibliotecaParser.apply(base, BibliotecaParser.parseChanges("""{"rev":801,"op":"baja","tipo":"canal","id":"c3"}"""), 801)
        store.save(out.biblioteca)
        val back = store.load()
        assertNotNull(back)
        assertEquals(801L, back!!.revision)
        assertEquals(setOf("c1", "c2"), back.canales.keys)
        assertEquals(listOf(B, A), back.canales.getValue("c2").ids.map { it.assetId })
        store.clear(); assertNull(store.load())
        dir.deleteRecursively()
    }
}
