package com.mivisor.tv.model

/**
 * 3f · Qué tipo de aparato es (la misma APK sirve para tele, tablet y móvil).
 *
 * Regla, de más a menos fiable:
 *  1. Android dice que es una tele (modo de interfaz TELEVISION) o tiene Leanback → TV.
 *  2. Sin pantalla táctil (cajas Android con mando) → TV.
 *  3. Con pantalla táctil: lado corto ≥ 600 dp → TABLET; si no, PHONE.
 * Ajustes › Pantalla › «Tipo de pantalla» puede forzarlo si un aparato se identifica mal.
 */
enum class DeviceClass(val id: String, val label: String) {
    TV("tv", "Televisión"), TABLET("tablet", "Tablet"), PHONE("phone", "Móvil");

    companion object {
        fun detect(tvMode: Boolean, leanback: Boolean, touchscreen: Boolean, smallestWidthDp: Int, override: String = "auto"): DeviceClass {
            entries.firstOrNull { it.id == override }?.let { return it }
            return when {
                tvMode || leanback -> TV
                !touchscreen -> TV
                smallestWidthDp >= 600 -> TABLET
                else -> PHONE
            }
        }

        /**
         * Densidad con la que se dibuja la app. Todo el diseño está hecho sobre un lienzo de 960 × 540 dp:
         *  - TV: el lado largo mide SIEMPRE 960 dp, sea la tele de 32" o de 75", 720p o 4K (algunas teles declaran una
         *    densidad rara y la app se veía diminuta o gigante).
         *  - Móvil (en horizontal): como mucho la densidad del sistema, y algo menor si la pantalla es muy baja, para que
         *    quepan al menos 440 dp de alto.
         *  - Tablet: la del sistema (los dedos necesitan el tamaño real).
         */
        const val TV_WIDTH_DP = 1024f

        fun density(device: DeviceClass, widthPx: Int, heightPx: Int, systemDensity: Float): Float {
            val long = maxOf(widthPx, heightPx).toFloat()
            val short = minOf(widthPx, heightPx).toFloat()
            return when (device) {
                // 3k · 1024 dp de ancho (antes 960): todo un 6 % más pequeño en la tele; se leía grande y cortado.
                TV -> if (long > 0) long / TV_WIDTH_DP else systemDensity
                PHONE -> if (short > 0) minOf(systemDensity, short / 440f) else systemDensity
                TABLET -> systemDensity
            }
        }
    }
}
