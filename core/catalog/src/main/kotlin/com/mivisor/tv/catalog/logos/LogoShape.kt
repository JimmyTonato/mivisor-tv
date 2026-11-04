package com.mivisor.tv.catalog.logos

/**
 * 3f · ¿El logo trae su propio fondo? Se decide mirando sus píxeles (ARGB), sin Android, para poder probarlo.
 *
 *  - [card] = lo opaco llena casi todo su recuadro (≥ 90 %) y ocupa buena parte de la imagen (≥ 45 %): es una tarjeta
 *    (fondo blanco, azul…, quizá con su marco). Se recorta a [crop] (sin margen transparente ni el 4 % del borde, donde
 *    van sus esquinas redondeadas y su marco) y la tarjeta de la app toma su color de borde [background] (ARGB), si
 *    es uniforme.
 *  - Si no, es un dibujo sobre transparente; [needsLight] = es oscuro y hace falta una placa clara para leerlo.
 */
class LogoShape(val card: Boolean, val crop: IntArray?, val background: Int?, val needsLight: Boolean) {
    companion object {
        fun analyze(w: Int, h: Int, pixel: (Int, Int) -> Int): LogoShape {
            if (w <= 0 || h <= 0) return LogoShape(false, null, null, false)
            val step = maxOf(1, maxOf(w, h) / 48)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            var lum = 0.0; var opaque = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val c = pixel(x, y)
                    if (alpha(c) >= 128) {
                        opaque++
                        lum += luminance(c)
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                    x += step
                }
                y += step
            }
            if (opaque == 0) return LogoShape(false, null, null, false)
            maxX = minOf(w - 1, maxX + step - 1)
            maxY = minOf(h - 1, maxY + step - 1)
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            var inBox = 0; var filled = 0
            y = minY
            while (y <= maxY) {
                var x = minX
                while (x <= maxX) {
                    inBox++
                    if (alpha(pixel(x, y)) >= 128) filled++
                    x += step
                }
                y += step
            }
            val fill = filled.toFloat() / maxOf(1, inBox)
            val coverage = bw.toFloat() * bh / (w.toFloat() * h)
            if (fill < 0.9f || coverage < 0.45f) return LogoShape(false, null, null, needsLight = lum / opaque < 0.28)
            val inset = maxOf(1, (minOf(bw, bh) * 0.04f).toInt())
            val cx = minOf(w - 1, minX + inset)
            val cy = minOf(h - 1, minY + inset)
            val cw = (bw - 2 * inset).coerceIn(1, w - cx)
            val ch = (bh - 2 * inset).coerceIn(1, h - cy)
            return LogoShape(true, intArrayOf(cx, cy, cw, ch), edge(cx, cy, cw, ch, pixel), false)
        }

        /** Color mayoritario del borde del recorte (≥ 60 % de los puntos), como media real de esos puntos. */
        private fun edge(x0: Int, y0: Int, w: Int, h: Int, pixel: (Int, Int) -> Int): Int? {
            val pts = ArrayList<Int>()
            val step = maxOf(1, maxOf(w, h) / 40)
            val top = y0 + minOf(1, h - 1)
            val bottom = y0 + maxOf(0, h - 2)
            val left = x0 + minOf(1, w - 1)
            val right = x0 + maxOf(0, w - 2)
            var i = 0
            while (i < w) { pts += pixel(x0 + i, top); pts += pixel(x0 + i, bottom); i += step }
            i = 0
            while (i < h) { pts += pixel(left, y0 + i); pts += pixel(right, y0 + i); i += step }
            val groups = pts.groupBy { quant(it) }
            val best = groups.maxByOrNull { it.value.size } ?: return null
            if (best.value.size < pts.size * 0.6f) return null
            val r = best.value.sumOf { (it shr 16) and 0xFF } / best.value.size
            val g = best.value.sumOf { (it shr 8) and 0xFF } / best.value.size
            val b = best.value.sumOf { it and 0xFF } / best.value.size
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun quant(c: Int) = (((c shr 20) and 0xF) shl 8) or (((c shr 12) and 0xF) shl 4) or ((c shr 4) and 0xF)
        private fun alpha(c: Int) = (c ushr 24) and 0xFF
        private fun luminance(c: Int): Double =
            (0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)) / 255.0
    }
}
