package com.mivisor.tv.ui

import androidx.compose.runtime.mutableStateListOf

/** Pantallas de MiVisor TV. Las cinco primeras son las pestañas de la barra superior. */
sealed class Route {
    object Home : Route()
    object Agenda : Route()
    object Channels : Route()
    object Search : Route()
    object MyList : Route()
    object Settings : Route()
    data class Channel(val id: String) : Route()
    data class Player(val channelId: String, val chosen: String? = null) : Route()
    data class AddProvider(val prefill: String? = null, val fromWizard: Boolean = false) : Route()
    object Wizard : Route()

    val isTab: Boolean get() = this in TABS

    companion object {
        val TABS: List<Route> get() = listOf(Home, Agenda, Channels, Search, MyList, Settings)
    }
}

/** Pila de navegación propia (sin dependencias): atrás siempre vuelve a la pantalla anterior. */
class Nav(start: Route) {
    val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()

    fun go(route: Route) {
        if (current == route) return
        stack.add(route)
    }

    /** Pestaña: la pila vuelve a empezar en ella. */
    fun tab(route: Route) {
        stack.clear()
        stack.add(route)
    }

    /** Sustituye la pantalla actual (p. ej. del asistente a Inicio). */
    fun replace(route: Route) {
        stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    /** false = no hay a dónde volver (Inicio): la Activity decide. */
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (current != Route.Home && current != Route.Wizard) {
            tab(Route.Home)
            return true
        }
        return false
    }
}
