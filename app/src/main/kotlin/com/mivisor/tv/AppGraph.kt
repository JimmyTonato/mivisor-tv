package com.mivisor.tv

import android.content.Context
import com.google.gson.Gson
import com.mivisor.tv.catalog.AgendaRepository
import com.mivisor.tv.catalog.PayloadDecoder
import com.mivisor.tv.catalog.RawCache
import com.mivisor.tv.catalog.StreamRepository
import com.mivisor.tv.catalog.TvChannelsRepository
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.LogoEntry
import com.mivisor.tv.catalog.logos.LogoRepository
import com.mivisor.tv.catalog.logos.LogoSource
import com.mivisor.tv.data.RemindersRepository
import kotlinx.coroutines.launch
import com.mivisor.tv.data.AgendaStore
import com.mivisor.tv.data.LogBook
import com.mivisor.tv.data.MyListRepository
import com.mivisor.tv.data.ProvidersRepository
import com.mivisor.tv.data.SourceBookImpl
import com.mivisor.tv.diagnostics.Redactor
import com.mivisor.tv.net.CompositeDnsResolver
import com.mivisor.tv.playback.PlaybackController
import com.mivisor.tv.storage.CodeVault
import com.mivisor.tv.storage.MvDatabase
import com.mivisor.tv.storage.Prefs
import com.mivisor.tv.warp.WarpController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import com.mivisor.tv.ui.screens.playSources

/**
 * Inyección de dependencias a mano: un único grafo para toda la app (sin Hilt ni KSP).
 * Todo vive mientras vive el proceso; nada trabaja en segundo plano.
 */
class AppGraph(context: Context) {
    val app: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = Prefs(app)
    val db = MvDatabase(app)
    val redactor = Redactor().apply { addSecret(BuildConfig.STREAM_DOMAIN) }
    val log = LogBook(db, CoroutineScope(SupervisorJob() + Dispatchers.IO), redactor)

    private val streams = StreamRepository(
        resolver = CompositeDnsResolver(),
        decoder = PayloadDecoder(Gson()),
        cache = RawCache(File(app.cacheDir, "txt_cache")),
        domain = BuildConfig.STREAM_DOMAIN
    )

    val agenda = AgendaStore(AgendaRepository(File(app.cacheDir, "agenda")), prefs, BuildConfig.AGENDA_URL, log, scope,
        com.mivisor.tv.catalog.CompetitionBook(File(app.filesDir, "competitions.json")))
    /** Logos (3c): channel-logos.json junto a agenda.json en mivisor-datos, con caché en filesDir/logos. */
    val logos = LogoRepository(File(app.filesDir, "logos"))
    val providers = ProvidersRepository(db, CodeVault(), prefs, streams, log, scope, logos, { LogoRepository.urlFor(agenda.url) },
        File(app.filesDir, "library-snapshot.json"),
        // 3j · Catálogo central de proveedores (rama providers de mivisor-datos), con copia local.
        catalogUrl = { com.mivisor.tv.catalog.datos.GitHubData.providersCatalog(agenda.url) },
        catalogDir = File(app.filesDir, "providers-catalog"))
    /** 3j · Banners de competiciones (rama banners de mivisor-datos), guardados en la memoria de la app. */
    val banners = com.mivisor.tv.data.BannerStore(app, scope, { agenda.url }, log)
    /** 4f · Biblioteca de canales de MiVisor Gold (rama biblioteca de mivisor-datos, cifrada). */
    val gold = com.mivisor.tv.data.GoldLibraryRepository(app, scope, { agenda.url }, providers, logos, log)
    /** 3o · Servidores del catálogo de Ace Stream (rama catalogo de mivisor-datos) para el motor. */
    val catalogHosts = com.mivisor.tv.data.CatalogHostsStore(app, scope, { agenda.url }, log)
    /** «Dónde verlo» de los eventos que no traen canales: TheSportsDB, solo al abrir el evento y con caché. */
    val tvChannels = TvChannelsRepository(File(app.cacheDir, "tvchannels"))
    val reminders = RemindersRepository(app, db, log, scope)
    val mylist = MyListRepository(db, scope)
    val warp = WarpController(app, scope)
    val sources = SourceBookImpl(app, db, warp, providers, mylist, log, prefs, scope).also { it.onGoldSourceFailed = { gold.onSourceFailed() } }
    val player = PlaybackController(app, sources)

    /** 3i · Precarga del canal enfocado (1,5 s con el foco encima): el motor empieza antes a buscar emisores. */
    val prewarm = com.mivisor.tv.data.Prewarm(
        app, scope,
        bestSource = { ch ->
            sources.order(ch.id, ch.playSources(), null).firstOrNull()
        },
        playing = { player.currentContentId() },
        enabled = { prefs.prewarm },
        log = {},
        previewing = { preview.contentId() }
    )

    /** 3k · Vista previa en vídeo (sin sonido) del canal enfocado en Canales. */
    val preview: com.mivisor.tv.data.ChannelPreview by lazy {
        com.mivisor.tv.data.ChannelPreview(
            app, scope,
            bestSource = { ch ->
                sources.order(ch.id, ch.playSources(), null).firstOrNull()
            },
            enabled = { prefs.channelPreview ?: com.mivisor.tv.data.ChannelPreview.defaultOn(app) },
            keepWarm = { cid -> prewarm.keep(cid) },
            log = { log.add("Canales", it) }
        )
    }

    /** 3d: informe, «Probar todo», copias cifradas y verificador manual. */
    val diagnostics by lazy { com.mivisor.tv.data.Diagnostics(this) }

    /** «Canal anterior»: se apunta cada canal que empieza a verse. */
    fun watched(channelId: String) {
        val last = prefs.lastChannel
        if (last == channelId) return
        if (last != null) prefs.previousChannel = last
        prefs.lastChannel = channelId
    }

    /** El canal al que lleva «Canal anterior» desde [current] (o desde el último visto). */
    fun previousOf(current: String?): String? {
        val last = prefs.lastChannel
        val prev = prefs.previousChannel
        return if (current != null && current == last) prev else last ?: prev
    }

    /** Al abrir la app: lo guardado al instante y después la red. */
    fun start() {
        com.mivisor.tv.outpace.EngineManager.fullP2p = prefs.fullP2p    // 3s · antes de que arranque el motor
        sources.load()
        mylist.load()
        reminders.load()
        gold.start()          // 4f · antes que los proveedores: si hay Biblioteca, es lo primero que se ve
        providers.start()
        providers.startupSync()
        agenda.load()
        banners.start()
        catalogHosts.start()
        // Logos que trae la agenda (futbolenlatv): se aprovechan para los canales de tus proveedores.
        scope.launch {
            agenda.state.collect { st ->
                val entries = st.agenda?.events.orEmpty().flatMap { it.channels }
                    .mapNotNull { c -> c.logo?.takeIf { LogoCatalog.usable(it) }?.let { LogoEntry(c.name, it, LogoSource.FUTBOLENLATV) } }
                    .distinctBy { it.name.lowercase() }
                providers.harvestLogos(entries)
            }
        }
    }
}
