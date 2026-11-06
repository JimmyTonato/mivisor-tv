package com.mivisor.tv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.mivisor.tv.data.BannerStore
import com.mivisor.tv.ui.design.MvColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 3j · Banners disponibles para cualquier pantalla (null = sin banners: fondo de siempre). */
val LocalBanners = staticCompositionLocalOf<BannerStore?> { null }

/**
 * 3j · Imágenes de banner decodificadas al tamaño en que se pintan (inSampleSize): una tarjeta de 320 dp no carga
 * la imagen de 2560 px. Caché en memoria acotada (1/16 del heap, máx. 24 MB).
 */
object BannerImages {
    private val memory: LruCache<String, Bitmap> by lazy {
        val kb = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceIn(4 * 1024, 24 * 1024)
        object : LruCache<String, Bitmap>(kb) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }
    }

    fun clear() = memory.evictAll()

    private fun key(file: File, width: Int) = "${file.path}|${file.lastModified()}|$width"

    suspend fun load(file: File, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            // Aparatos justos de memoria (TV stick): como mucho 1280 px de ancho (se nota poco y ocupa la mitad).
            val target = if (Runtime.getRuntime().maxMemory() < 200L * 1024 * 1024) minOf(widthPx, 1280) else widthPx
            memory.get(key(file, target))?.let { return@runCatching it }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, o)
            if (o.outWidth <= 0) return@runCatching null
            var sample = 1
            while (o.outWidth / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                // Y luego escalado exacto al ancho pedido (2560 → 1920 en la portada: 8 MB en vez de 15).
                val w = o.outWidth / sample
                if (w > target) { inScaled = true; inDensity = w; inTargetDensity = target }
            }
            BitmapFactory.decodeFile(file.path, opts)?.also { memory.put(key(file, target), it) }
        }.getOrNull()
    }
}

/** El banner de la competición de un evento, a [widthPx] de ancho; null mientras no hay (o no existe). */
@Composable
fun rememberBanner(sport: String?, competition: String?, widthPx: Int): ImageBitmap? {
    val store = LocalBanners.current ?: return null
    val catalog by store.catalog.collectAsState()
    val version by store.version.collectAsState()
    val entry = catalog?.find(sport, competition)
    val bitmap by produceState<ImageBitmap?>(null, entry?.path, version, widthPx) {
        value = entry?.let { e -> store.file(e)?.let { f -> BannerImages.load(f, widthPx)?.asImageBitmap() } }
    }
    return bitmap
}

/**
 * Pinta el banner recortado al hueco (centrado: en una portada ancha se ve la franja central y 320–1120 del diseño).
 * [scrim] oscurece a la izquierda y abajo para que se lea el texto. Devuelve si lo ha pintado.
 */
@Composable
fun BannerBackground(image: ImageBitmap?, modifier: Modifier = Modifier.fillMaxSize(), scrim: Boolean = true): Boolean {
    if (image == null) return false
    Box(modifier) {
        Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center)
        if (scrim) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to MvColors.Bg.copy(alpha = 0.78f), 0.45f to MvColors.Bg.copy(alpha = 0.25f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to MvColors.Bg.copy(alpha = 0.7f))))
        }
    }
    return true
}
