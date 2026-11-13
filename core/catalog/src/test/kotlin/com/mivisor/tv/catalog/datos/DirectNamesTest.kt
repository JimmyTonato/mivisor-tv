package com.mivisor.tv.catalog.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 4h · «Abrir un ID»: el nombre del canal se saca del descriptor y se apunta en el historial. */
class DirectNamesTest {
    private val a = "a".repeat(40)
    private val b = "b".repeat(40)

    @Test fun tituloDeLaLineaDelMotor() {
        assertEquals("Movistar La Liga", DirectStream.titleOfDescriptorLine("infohash=e4a2 firmado=si pieza=1048576 titulo=Movistar La Liga"))
        assertNull(DirectStream.titleOfDescriptorLine("infohash=e4a2 firmado=si pieza=1048576 titulo=-"))
        assertNull(DirectStream.titleOfDescriptorLine(null))
    }

    @Test fun ponNombreSoloSiNoTenia() {
        val recent = listOf("$a\t", "$b\tEl partido de Juan")
        assertEquals(listOf("$a\tESPN 2 [BE]", "$b\tEl partido de Juan"), DirectStream.nameRecent(recent, a, "ESPN 2 [BE]"))
        assertNull(DirectStream.nameRecent(recent, b, "ESPN"))            // el nombre del usuario manda
        assertEquals(listOf("$a\tX", "$b\tEl partido de Juan"), DirectStream.nameRecent(listOf("$b\tEl partido de Juan"), a, "X")) // no estaba: arriba
        assertNull(DirectStream.nameRecent(recent, a, "   "))
    }
}
