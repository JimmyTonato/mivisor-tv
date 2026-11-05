import com.mivisor.tv.catalog.CatalogBuilder
import com.mivisor.tv.catalog.ChannelMatcher
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.catalog.tv.ChannelSeriesGrouping
import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.Picons
import com.mivisor.tv.model.StreamItem
import java.io.File
import kotlin.random.Random

val brands = listOf("M+ LaLiga", "Movistar LaLiga", "M+ Liga de Campeones", "Movistar Liga de Campeones", "DAZN", "DAZN LaLiga", "DAZN F1",
    "Eurosport", "LaLiga TV Hypermotion", "M+ Deportes", "Movistar Deportes", "M+ Vamos", "M+ Golf", "Sky Sports Main Event", "Sky Sports Football",
    "beIN Sports", "ESPN", "Fox Sports", "TNT Sports", "Canal+ Sport", "Arena Sport", "Sport TV", "Eleven Sports", "Premier Sports",
    "TUDN", "Win Sports", "Gol TV", "Teledeporte", "Antena 3", "La 1", "Cuatro", "Calle 13", "Fútbol Replay", "Más Fútbol", "Real Madrid TV",
    "Barça TV", "Movistar Plus+", "M+ Ellas V", "DAZN Baloncesto", "NBA TV", "F1 TV", "MotoGP", "Ufc Fight Pass", "Liga Endesa", "Tennis Channel")
val prefixes = listOf("", "", "", "ES | ", "[ES] ", "🇪🇸 ", "SPAIN: ", "VIP ", "ES - ", "|ES| ")
val quals = listOf("", " HD", " FHD", " 1080", " 4K", " SD", " 720p", " (Op 2)", " [ES]", " UHD", " 1080p50", " HEVC", " (Backup)", " *", " -2-")
fun hex(r: Random) = (1..40).map { "0123456789abcdef"[r.nextInt(16)] }.joinToString("")

fun main(args: Array<String>) {
    val out = StringBuilder()
    for (seed in 1..6) {
        val r = Random(seed)
        val providers = (1..12).map { p ->
            val items = (1..400).map {
                val b = brands[r.nextInt(brands.size)]
                val n = when (r.nextInt(5)) { 0, 1 -> ""; 2 -> " " + (1 + r.nextInt(6)); 3 -> "" + (1 + r.nextInt(4)); else -> " " + (1 + r.nextInt(12)) }
                val name = prefixes[r.nextInt(prefixes.size)] + (if (r.nextInt(7) == 0) b.uppercase() else b) + n + quals[r.nextInt(quals.size)]
                val url = when (r.nextInt(20)) { 0 -> "http://x.example/live/$it.m3u8"; 1 -> "acestream://" + hex(r).uppercase(); 2 -> "acestream://bad"; else -> "acestream://" + hex(r) }
                StreamItem(name = name, url = url)
            }
            StreamItem(name = "Proveedor $p", subLinks = items.chunked(60).mapIndexed { i, c -> StreamItem(name = "Sección $i", subLinks = c) })
        }
        val picNames = brands.flatMap { b -> listOf("$b.png", "$b 1.png", "$b 2.png", "$b 3.png") }.map { it.replace("Movistar ", "M+ ") }.distinct().filter { r.nextInt(3) != 0 }
        val resolver = ChannelLogoResolver(LogoCatalog.EMPTY, Picons.entries(picNames))
        val cats = providers.mapIndexed { i, p ->
            val c = CatalogBuilder.build(listOf(p))
            val all = CatalogBuilder.build(listOf(p), aceOnly = false)
            out.append("CAT $seed/$i ${c.channelCount} ${all.channelCount}\n")
            c.sections.forEach { s -> s.channels.forEach { ch -> out.append(" ${ch.id}|${ch.name}|${ch.url}|${ch.kind}\n") } }
            ProviderCatalog("p$i", "Proveedor $i", c)
        }
        val lib = TvLibrary.build(cats, resolver)
        out.append("LIB $seed ${lib.channels.size}\n")
        lib.channels.forEach { c ->
            out.append(" ${c.id}|${c.name}|${c.brandKey}|${c.brandName}|${c.family}|${c.logo}|${c.category}|${c.initials}\n")
            c.sources.forEach { s -> out.append("   ${s.contentId}|${s.providerId}|${s.quality}|${s.qualityLabel}|${s.variantName}\n") }
        }
        lib.sections.forEach { s -> out.append("SEC ${s.key}|${s.name}|${s.channels.map { it.id }}|${s.logo}\n") }
        ChannelSeriesGrouping.group(lib.channels).forEach { s -> out.append("SER ${s.key}|${s.name}|${s.channels.map { it.id }}\n") }
        val flat = cats.flatMap { it.catalog.sections.flatMap { s -> s.channels } }
        val m = ChannelMatcher(flat)
        (brands + brands.map { "$it 2" } + brands.map { it.uppercase() } + listOf("LaLiga DAZN", "Liga de Campeones", "DAZN 1", "M+ LaLiga TV", "Gol", "")).forEach { q ->
            out.append("MATCH $q -> ${m.match(q).map { it.url }}\n")
            out.append("MATCH3 $q -> ${m.match(q, 3).map { it.url }}\n")
        }
    }
    File(args[0]).writeText(out.toString())
    println("líneas: " + out.lines().size)
}
