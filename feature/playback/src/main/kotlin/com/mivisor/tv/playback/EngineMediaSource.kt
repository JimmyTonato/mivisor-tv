package com.mivisor.tv.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/**
 * 4c · Fuente HLS del motor. Hay canales de TV (H.264 entrelazado, 1080i) que nunca mandan IDR: sus
 * imágenes clave son cortes I normales. Sin [DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES]
 * el reproductor espera un IDR que no llega y se queda en «preparando la imagen» aunque el vídeo esté entrando.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object EngineMediaSource {
    const val TS_FLAGS = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES

    fun factory(http: DataSource.Factory): MediaSource.Factory =
        HlsMediaSource.Factory(http)
            .setExtractorFactory(DefaultHlsExtractorFactory(TS_FLAGS, /* exposeCea608WhenMissingDeclarations = */ true))
}
