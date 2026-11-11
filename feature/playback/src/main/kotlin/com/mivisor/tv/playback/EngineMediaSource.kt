package com.mivisor.tv.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/**
 * Fuentes del motor para Media3.
 *
 * - 4c · HLS con [DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES]: hay canales de TV (H.264
 *   entrelazado) cuyas imágenes clave son cortes I normales, sin IDR. Sin la bandera el reproductor espera un IDR que
 *   no llega.
 * - 4e · Plan B: el vídeo DIRECTO del motor (`.ts` continuo, sin empaquetar en HLS), con las mismas banderas. Se usa
 *   cuando llega vídeo pero el HLS no da imagen (formato que el empaquetador no sabe cortar).
 */
@androidx.annotation.OptIn(UnstableApi::class)
object EngineMediaSource {
    const val TS_FLAGS = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES

    fun factory(http: DataSource.Factory): MediaSource.Factory = Factory(http)

    private class Factory(http: DataSource.Factory) : MediaSource.Factory {
        private val hls = HlsMediaSource.Factory(http)
            .setExtractorFactory(DefaultHlsExtractorFactory(TS_FLAGS, /* exposeCea608WhenMissingDeclarations = */ true))
        private val direct = ProgressiveMediaSource.Factory(
            http,
            DefaultExtractorsFactory().setTsExtractorFlags(TS_FLAGS)
        )

        override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
            hls.setDrmSessionManagerProvider(provider)
            direct.setDrmSessionManagerProvider(provider)
            return this
        }

        override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
            hls.setLoadErrorHandlingPolicy(policy)
            direct.setLoadErrorHandlingPolicy(policy)
            return this
        }

        override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER)

        override fun createMediaSource(mediaItem: MediaItem): MediaSource =
            if (mediaItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) hls.createMediaSource(mediaItem)
            else direct.createMediaSource(mediaItem)
    }
}
