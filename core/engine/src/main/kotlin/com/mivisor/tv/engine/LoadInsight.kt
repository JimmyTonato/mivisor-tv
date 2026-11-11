package com.mivisor.tv.engine

/**
 * 4d · Qué está pasando mientras se espera la imagen, ya con emisores, en palabras claras.
 *
 * Se le pasa el estado del motor una vez por segundo ([observe]). Mira tres cosas:
 *  - si llega vídeo (bytes recibidos o, con un motor sin el parche 0009, la pieza por la que va);
 *  - si la emisión avanza (la última pieza que anuncian los emisores);
 *  - si hay piezas que no pasan la firma.
 */
class LoadInsight {
    enum class Kind {
        /** Normal: aún no hay motivo para preocuparse. */
        WAITING,
        /** Llega vídeo; se está llenando el búfer o esperando una imagen completa. */
        RECEIVING,
        /** Conectados, pero no nos mandan nada. */
        NO_DATA,
        /** Llegó algo y se cortó, y los emisores tampoco tienen nada nuevo: la emisión está parada. */
        STALLED,
        /** La emisión avanza, pero a nosotros no nos llega. */
        NOT_REACHING,
        /** Llegan piezas que no pasan la comprobación de firma. */
        SIGNATURE,
        /** Llega vídeo desde hace rato y no sale imagen. */
        NO_PICTURE
    }

    data class Insight(val kind: Kind, val status: String, val hint: String?)

    private var secs = 0
    private var lastData: Long? = null
    private var lastDataAt = 0
    private var everData = false
    private var lastEdge: Long? = null
    private var lastEdgeAt = 0
    private var edgeMoved = false
    private var lastRejects = -1L
    private var rejectsAt = -1

    /** Empieza de cero (otra fuente o recarga). */
    fun reset() {
        secs = 0; lastData = null; lastDataAt = 0; everData = false
        lastEdge = null; lastEdgeAt = 0; edgeMoved = false; lastRejects = -1; rejectsAt = -1
    }

    /** Un segundo más de espera con emisores. */
    fun observe(status: EngineStatus?, peers: Int): Insight {
        secs++
        if (status != null) {
            val data = status.downloaded ?: status.pos
            if (data != null) {
                val prev = lastData
                if (prev == null) {
                    if (data > 0 && status.downloaded != null) { everData = true; lastDataAt = secs }
                } else if (data > prev) {
                    everData = true; lastDataAt = secs
                }
                lastData = data
            }
            status.liveLast?.let { edge ->
                val prev = lastEdge
                if (prev == null || edge > prev) {
                    if (prev != null) edgeMoved = true
                    lastEdge = edge; lastEdgeAt = secs
                }
            }
            if (lastRejects >= 0 && status.signatureRejects > lastRejects) rejectsAt = secs
            lastRejects = status.signatureRejects
        }
        return classify(peers, status?.downloadRate)
    }

    private fun classify(peers: Int, rate: Long?): Insight {
        val who = "$peers ${if (peers == 1) "emisor" else "emisores"}"
        val sinceData = secs - lastDataAt
        val sinceEdge = secs - lastEdgeAt
        val receiving = everData && sinceData < STALL_SECS
        return when {
            rejectsAt > 0 && secs - rejectsAt < STALL_SECS && !receiving -> Insight(
                Kind.SIGNATURE, "Llegan piezas que no son de esta emisión",
                "Las piezas no pasan la comprobación de firma: quien emite ha cambiado. Prueba otra fuente o vuelve a abrir el canal."
            )
            everData && sinceData >= STALL_SECS && (!edgeMoved || sinceEdge >= STALL_SECS) -> Insight(
                Kind.STALLED, "La emisión está parada",
                "Llegaron unos segundos de vídeo y se cortó: los emisores no reciben nada nuevo de quien emite. " +
                    "Suele ser que el canal se ha caído o ha cortado. Prueba otra fuente o vuelve en un rato."
            )
            everData && sinceData >= STALL_SECS -> Insight(
                Kind.NOT_REACHING, "Hay emisión, pero no nos llega",
                "Los emisores tienen vídeo nuevo, pero no nos lo mandan (van saturados). Suele arreglarse solo; si no, prueba otra fuente."
            )
            !everData && secs >= NO_DATA_SECS -> Insight(
                Kind.NO_DATA, "Conectado a $who, pero no mandan vídeo",
                if (lastEdge != null && !edgeMoved && secs >= STALL_SECS)
                    "Están conectados pero no envían nada y la emisión no avanza: puede que el canal se haya caído. Prueba otra fuente."
                else "Están conectados pero aún no envían nada. Suele ser que van justos de subida. Espera unos segundos o prueba otra fuente."
            )
            receiving && secs >= NO_PICTURE_SECS -> Insight(
                Kind.NO_PICTURE, "Llega vídeo, pero aún no sale imagen",
                "El vídeo está llegando${rateText(rate)}, pero el reproductor no consigue empezar. Si sigue así, " +
                    "manda el informe desde Ajustes › Diagnóstico: puede ser un formato que no conocemos."
            )
            receiving -> Insight(
                Kind.RECEIVING, "Recibiendo vídeo de $who · preparando la imagen…",
                if (secs >= SLOW_HINT_SECS) "Llega más despacio de lo normal${rateText(rate)}: se está llenando el búfer." else null
            )
            else -> Insight(Kind.WAITING, "Conectado a $who · preparando la imagen…", null)
        }
    }

    private fun rateText(rate: Long?): String =
        if (rate == null || rate <= 0) "" else " (${"%.1f".format(java.util.Locale.ROOT, rate * 8 / 1_000_000.0)} Mb/s)"

    companion object {
        /** Segundos sin datos nuevos para decir que se ha cortado. */
        const val STALL_SECS = 12
        const val NO_DATA_SECS = 10
        const val SLOW_HINT_SECS = 15
        const val NO_PICTURE_SECS = 30
    }
}
