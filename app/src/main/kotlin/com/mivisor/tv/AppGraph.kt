package com.mivisor.tv

import android.content.Context
import com.google.gson.Gson
import com.mivisor.tv.catalog.AgendaRepository
import com.mivisor.tv.catalog.PayloadDecoder
import com.mivisor.tv.catalog.RawCache
import com.mivisor.tv.catalog.StreamRepository
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

    val providers = ProvidersRepository(db, CodeVault(), prefs, streams, log, scope)
    val agenda = AgendaStore(AgendaRepository(File(app.cacheDir, "agenda")), prefs, BuildConfig.AGENDA_URL, log, scope)
    val mylist = MyListRepository(db, scope)
    val warp = WarpController(app, scope)
    val sources = SourceBookImpl(app, db, warp, providers, mylist, log, scope)
    val player = PlaybackController(app, sources)

    /** Al abrir la app: lo guardado al instante y después la red. */
    fun start() {
        sources.load()
        mylist.load()
        providers.start()
        agenda.load()
    }
}
