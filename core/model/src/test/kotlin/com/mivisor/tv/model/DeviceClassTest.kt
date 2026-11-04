package com.mivisor.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassTest {
    @Test fun detecta() {
        assertEquals(DeviceClass.TV, DeviceClass.detect(tvMode = true, leanback = false, touchscreen = false, smallestWidthDp = 540))
        assertEquals(DeviceClass.TV, DeviceClass.detect(tvMode = false, leanback = true, touchscreen = true, smallestWidthDp = 720))
        assertEquals(DeviceClass.TV, DeviceClass.detect(tvMode = false, leanback = false, touchscreen = false, smallestWidthDp = 360))   // caja con mando
        assertEquals(DeviceClass.TABLET, DeviceClass.detect(tvMode = false, leanback = false, touchscreen = true, smallestWidthDp = 800))
        assertEquals(DeviceClass.PHONE, DeviceClass.detect(tvMode = false, leanback = false, touchscreen = true, smallestWidthDp = 392))
        assertEquals(DeviceClass.TABLET, DeviceClass.detect(tvMode = true, leanback = true, touchscreen = false, smallestWidthDp = 540, override = "tablet"))
    }

    @Test fun densidad() {
        // Tele 1080p, 4K o con densidad «tvdpi»: siempre 960 dp de ancho.
        assertEquals(2f, DeviceClass.density(DeviceClass.TV, 1920, 1080, 1.33f), 0.001f)
        assertEquals(4f, DeviceClass.density(DeviceClass.TV, 3840, 2160, 2f), 0.001f)
        assertEquals(1.3333f, DeviceClass.density(DeviceClass.TV, 1280, 720, 2f), 0.001f)
        // Móvil 2400 × 1080 a 2,75: baja a 1080 / 440 ≈ 2,45 para que quepan 440 dp de alto.
        assertEquals(1080f / 440f, DeviceClass.density(DeviceClass.PHONE, 2400, 1080, 2.75f), 0.001f)
        assertEquals(2f, DeviceClass.density(DeviceClass.PHONE, 2400, 1080, 2f), 0.001f)
        assertEquals(2f, DeviceClass.density(DeviceClass.TABLET, 2560, 1600, 2f), 0.001f)
    }
}
