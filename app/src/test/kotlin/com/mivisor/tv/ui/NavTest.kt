package com.mivisor.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTest {
    @Test fun atrasVuelveALaPantallaAnteriorYLuegoAInicio() {
        val nav = Nav(Route.Home)
        nav.tab(Route.Channels)
        nav.go(Route.Channel("dazn laliga"))
        nav.go(Route.Player("dazn laliga"))
        assertTrue(nav.back()); assertEquals(Route.Channel("dazn laliga"), nav.current)
        assertTrue(nav.back()); assertEquals(Route.Channels, nav.current)
        assertTrue(nav.back()); assertEquals(Route.Home, nav.current)
        assertFalse(nav.back())
    }

    @Test fun noApilaDosVecesLaMismaPantalla() {
        val nav = Nav(Route.Home)
        nav.go(Route.Search); nav.go(Route.Search)
        assertEquals(2, nav.stack.size)
    }
}
