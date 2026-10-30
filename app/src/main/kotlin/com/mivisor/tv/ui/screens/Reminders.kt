package com.mivisor.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.Reminder
import com.mivisor.tv.model.ReminderPolicy
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType

/** El mejor canal tuyo para un recordatorio: el del evento en la agenda si sigue; si no, por los nombres guardados. */
fun channelForReminder(r: Reminder, graph: AppGraph, library: TvLibrary): TvChannel? {
    val event = graph.agenda.state.value.agenda?.events?.firstOrNull { it.id == r.eventId }
    val candidates = if (event != null) channelsFor(event, library) else r.channels.flatMap { library.forAgenda(it, 3) }.distinctBy { it.id }
    return candidates.maxByOrNull { ch -> graph.sources.bestScore(ch.sources.map { it.contentId }) }
}

/**
 * Aviso de recordatorio (3c), encima de cualquier pantalla y solo con la app abierta.
 * «Ver ahora» abre el mejor canal tuyo (el motor ya se dejó arrancado al saltar el aviso).
 */
@Composable
fun ReminderNotice(graph: AppGraph, nav: Nav, onPlaying: () -> Unit) {
    val notice by graph.reminders.notice.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val r = notice ?: return
    val first = remember(r.eventId) { FocusRequester() }
    val channel = channelForReminder(r, graph, lib.library)
    BackHandler { graph.reminders.dismiss() }
    Box(Modifier.fillMaxSize().padding(end = MvDims.SafeX, bottom = MvDims.SafeY), contentAlignment = Alignment.BottomEnd) {
        MvPanel(Modifier.width(520.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MvTag("RECORDATORIO", MvColors.Soon, filled = true)
                Spacer(Modifier.width(10.dp))
                MvText(ReminderPolicy.label(r, System.currentTimeMillis()), MvType.SmallStrong, color = MvColors.Soon)
            }
            Spacer(Modifier.height(10.dp))
            MvText(r.title, MvType.Heading, maxLines = 2)
            MvText(
                channel?.let { "En ${it.name} · Outpace ya está preparado" } ?: "Ninguno de tus proveedores tiene un canal de este evento",
                MvType.Small, maxLines = 2
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (channel != null) MvButton("Ver ahora", {
                    graph.reminders.dismiss()
                    onPlaying()
                    nav.go(Route.Player(channel.id))
                }, icon = MvIcons.Play, primary = true, focusRequester = first)
                MvButton("Dónde verlo", { graph.reminders.dismiss(); nav.go(Route.Event(r.eventId)) }, icon = MvIcons.Layers,
                    focusRequester = if (channel == null) first else null)
                MvButton("Cerrar", { graph.reminders.dismiss() }, icon = MvIcons.Close)
            }
        }
    }
    InitialFocus(first, r.eventId)
}
