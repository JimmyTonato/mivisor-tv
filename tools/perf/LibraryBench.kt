import com.mivisor.tv.catalog.CatalogBuilder
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.catalog.tv.ChannelSeriesGrouping
import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.Picons
import com.mivisor.tv.model.StreamItem
import kotlin.random.Random

val brands = listOf("M+ LaLiga", "Movistar LaLiga", "M+ Liga de Campeones", "DAZN", "DAZN LaLiga", "DAZN F1", "Eurosport", "LaLiga TV Hypermotion",
    "M+ Deportes", "M+ Vamos", "M+ Golf", "Sky Sports Main Event", "Sky Sports Football", "beIN Sports", "ESPN", "Fox Sports", "TNT Sports",
    "Canal+ Sport", "Arena Sport", "Sport TV", "Eleven Sports", "Premier Sports", "Setanta", "TUDN", "Win Sports", "Gol TV", "Teledeporte")
val quals = listOf("", " HD", " FHD", " 1080", " 4K", " SD", " 720p", " (Op 2)", " [ES]", " UHD")
fun hex(r: Random) = (1..40).map { "0123456789abcdef"[r.nextInt(16)] }.joinToString("")

fun main(args: Array<String>) {
    val perProv = args.getOrNull(0)?.toInt() ?: 600
    val r = Random(1)
    val providers = (1..12).map { p ->
        val items = (1..perProv).map {
            val b = brands[r.nextInt(brands.size)]
            val n = if (r.nextInt(3) == 0) "" else " " + (1 + r.nextInt(12))
            StreamItem(name = b + n + quals[r.nextInt(quals.size)], url = "acestream://" + hex(r))
        }
        StreamItem(name = "Proveedor $p", subLinks = items.chunked(50).mapIndexed { i, c -> StreamItem(name = "Sección $i", subLinks = c) })
    }
    val picNames = (1..3500).map { i -> (if (i % 3 == 0) brands[i % brands.size] + " " + (i % 15) else "Canal Genérico $i") + ".png" }
    repeat(args.getOrNull(1)?.toInt() ?: 3) { round ->
        var t = System.nanoTime()
        val resolver = ChannelLogoResolver(LogoCatalog.EMPTY, Picons.entries(picNames))
        val tRes = (System.nanoTime() - t) / 1e6
        t = System.nanoTime()
        val cats = providers.mapIndexed { i, p ->
            val c = CatalogBuilder.build(listOf(p))
            CatalogBuilder.build(listOf(p), aceOnly = false)
            ProviderCatalog("p$i", "P$i", c)
        }
        val tCat = (System.nanoTime() - t) / 1e6
        t = System.nanoTime()
        val lib = TvLibrary.build(cats, resolver)
        val tLib = (System.nanoTime() - t) / 1e6
        t = System.nanoTime()
        lib.providers.forEach { ChannelSeriesGrouping.group(it.channels) }
        ChannelSeriesGrouping.group(lib.channels)
        val tSer = (System.nanoTime() - t) / 1e6
        println("ronda $round: canales=${cats.sumOf { it.catalog.channelCount }} -> ${lib.channels.size} · resolver ${"%.0f".format(tRes)} ms · catálogos ${"%.0f".format(tCat)} ms · biblioteca ${"%.0f".format(tLib)} ms · series ${"%.0f".format(tSer)} ms")
    }
}
