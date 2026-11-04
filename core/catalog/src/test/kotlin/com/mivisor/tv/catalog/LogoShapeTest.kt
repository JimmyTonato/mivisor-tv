package com.mivisor.tv.catalog

import com.mivisor.tv.catalog.logos.LogoShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoShapeTest {
    private val clear = 0x00000000
    private val white = 0xFFFFFFFF.toInt()
    private val grey = 0xFF888888.toInt()
    private val navy = 0xFF0A1E5A.toInt()
    private val red = 0xFFD0202A.toInt()

    @Test fun tarjetaBlancaConMarcoYEsquinas() {
        // 200 × 120: margen transparente de 10 px, marco gris de 3 px, fondo blanco y un texto rojo en el centro.
        val s = LogoShape.analyze(200, 120) { x, y ->
            val inCard = x in 10..189 && y in 10..109
            val corner = (x < 16 || x > 183) && (y < 16 || y > 103)
            when {
                !inCard || corner -> clear
                x < 13 || x > 186 || y < 13 || y > 106 -> grey
                x in 70..130 && y in 50..70 -> red
                else -> white
            }
        }
        assertTrue(s.card)
        assertEquals(white, s.background)          // la tarjeta de la app se pinta de blanco: sin doble borde
        val c = s.crop!!
        assertTrue("recorta el margen y el marco: ${c.toList()}", c[0] >= 13 && c[1] >= 13 && c[0] + c[2] <= 188 && c[1] + c[3] <= 108)
    }

    @Test fun logoSueltoSobreTransparente() {
        val s = LogoShape.analyze(200, 120) { x, y -> if (x in 40..160 && y in 50..70 && (x / 10) % 2 == 0) white else clear }
        assertFalse(s.card)
        assertFalse(s.needsLight)
        assertNull(s.crop)
    }

    @Test fun logoOscuroPideLaPlacaClara() {
        val s = LogoShape.analyze(100, 100) { x, y -> if ((x - 50) * (x - 50) + (y - 50) * (y - 50) < 300) navy else clear }
        assertFalse(s.card)
        assertTrue(s.needsLight)
    }

    @Test fun fondoAzulUniformeSinMarco() {
        val s = LogoShape.analyze(160, 90) { x, y -> if (x in 60..100 && y in 30..60) white else navy }
        assertTrue(s.card)
        assertNotNull(s.background)
        assertEquals(navy, s.background)
    }

    @Test fun fotoSinColorDominanteNoInventaFondo() {
        val s = LogoShape.analyze(120, 120) { x, y -> (0xFF shl 24) or ((x * 2) shl 16) or ((y * 2) shl 8) or ((x + y) % 256) }
        assertTrue(s.card)
        assertNull(s.background)                    // se pinta recortado a sangre (Crop)
    }
}
