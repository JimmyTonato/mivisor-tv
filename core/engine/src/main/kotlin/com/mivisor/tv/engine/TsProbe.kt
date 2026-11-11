package com.mivisor.tv.engine

/**
 * 4e · «Caja negra»: resume en una línea qué trae el vídeo de un canal (unos segundos de MPEG-TS), para el informe.
 *
 * Así, cuando un canal no da imagen, el informe de Diagnóstico ya dice el formato y si hay puntos de arranque, sin
 * hacer capturas de red. No guarda el vídeo: solo el resumen.
 */
object TsProbe {
    private const val TS = 188

    data class Result(
        val bytes: Int,
        val packets: Int,
        val syncErrors: Int,
        /** Tipos de flujo del PMT: pid → stream_type. */
        val streams: Map<Int, Int>,
        val videoPid: Int?,
        val videoCodec: String?,
        val pesStarts: Int,
        val randomAccess: Int,
        /** Imágenes clave por tipo (p. ej. «IDR» 3, «I sin IDR» 5). */
        val keyframes: Map<String, Int>
    ) {
        val startPoints: Int get() = maxOf(randomAccess, keyframes.values.sum())

        fun summary(): String = buildString {
            if (packets == 0) {
                append("sin vídeo (${bytes} bytes, sin paquetes MPEG-TS)")
                return@buildString
            }
            append(videoCodec ?: "vídeo desconocido")
            val audio = streams.filterKeys { it != videoPid }.values.mapNotNull { audioName(it) }
            if (audio.isNotEmpty()) append(" + ").append(audio.joinToString(", "))
            append(" · ${bytes / 1024} KB, ${pesStarts} imágenes")
            if (keyframes.isEmpty() && randomAccess == 0) append(" · NINGÚN punto de arranque reconocible")
            else {
                append(" · puntos de arranque: ")
                append((keyframes.map { "${it.key} ${it.value}" } + listOfNotNull(randomAccess.takeIf { it > 0 }?.let { "marcados $it" })).joinToString(", "))
            }
            if (syncErrors > 0) append(" · $syncErrors paquetes dañados")
            val types = streams.values.joinToString(",") { "0x%02x".format(it) }
            append(" · tipos [$types]")
        }
    }

    fun videoName(type: Int): String? = when (type) {
        0x01 -> "MPEG-1"
        0x02 -> "MPEG-2"
        0x10 -> "MPEG-4"
        0x1B -> "H.264"
        0x24 -> "HEVC"
        0x42 -> "AVS"
        0xD1 -> "Dirac"
        0xEA -> "VC-1"
        else -> null
    }

    fun audioName(type: Int): String? = when (type) {
        0x03, 0x04 -> "MP2"
        0x0F -> "AAC"
        0x11 -> "AAC-LATM"
        0x81 -> "AC-3"
        0x87 -> "E-AC-3"
        0x06 -> "privado (AC-3/subtítulos)"
        else -> null
    }

    fun analyze(data: ByteArray): Result {
        val start = (0 until minOf(TS, data.size)).maxByOrNull { o ->
            var n = 0
            var k = o
            while (k < data.size && n < 50) { if (data[k].u() == 0x47) n++ else break; k += TS }
            n
        } ?: 0
        var packets = 0
        var syncErrors = 0
        var pmtPid: Int? = null
        val streams = linkedMapOf<Int, Int>()
        val pes = ArrayList<Pair<Boolean, ByteArray>>() // (RAI, primeros bytes del PES)
        var cur: java.io.ByteArrayOutputStream? = null
        var curRai = false
        fun flush() { cur?.let { pes.add(curRai to it.toByteArray()) }; cur = null }
        var videoPid: Int? = null
        var off = start
        while (off + TS <= data.size) {
            if (data[off].u() != 0x47) { syncErrors++; off += TS; continue }
            packets++
            val pid = ((data[off + 1].u() and 0x1F) shl 8) or data[off + 2].u()
            val pusi = data[off + 1].u() and 0x40 != 0
            val afc = (data[off + 3].u() shr 4) and 3
            var p = off + 4
            val rai = afc and 2 != 0 && data[off + 4].u() >= 1 && data[off + 5].u() and 0x40 != 0
            if (afc and 2 != 0) p += 1 + data[off + 4].u()
            val end = off + TS
            if (afc and 1 != 0 && p < end) {
                if (pid == 0 && pusi && pmtPid == null) {
                    val t = p + 1 + data[p].u()
                    if (t + 12 <= end) pmtPid = ((data[t + 10].u() and 0x1F) shl 8) or data[t + 11].u()
                } else if (pid == pmtPid && pusi && streams.isEmpty()) {
                    val t = p + 1 + data[p].u()
                    if (t + 12 <= end) {
                        val sl = ((data[t + 1].u() and 0x0F) shl 8) or data[t + 2].u()
                        val pil = ((data[t + 10].u() and 0x0F) shl 8) or data[t + 11].u()
                        var q = t + 12 + pil
                        val stop = minOf(t + 3 + sl - 4, end)
                        while (q + 5 <= stop) {
                            val st = data[q].u()
                            val ep = ((data[q + 1].u() and 0x1F) shl 8) or data[q + 2].u()
                            val il = ((data[q + 3].u() and 0x0F) shl 8) or data[q + 4].u()
                            streams[ep] = st
                            q += 5 + il
                        }
                        videoPid = streams.entries.firstOrNull { videoName(it.value) != null }?.key
                    }
                } else if (pid == videoPid) {
                    if (pusi) { flush(); cur = java.io.ByteArrayOutputStream(); curRai = rai }
                    cur?.let { if (it.size() < 4096) it.write(data, p, end - p) }
                }
            }
            off += TS
        }
        flush()
        val codec = videoPid?.let { streams[it] }
        val keys = linkedMapOf<String, Int>()
        var raiCount = 0
        for ((rai, bytes) in pes) {
            if (rai) raiCount++
            if (bytes.size < 9) continue
            val es = 9 + bytes[8].u()
            if (es >= bytes.size) continue
            keyframeKind(codec, bytes, es)?.let { keys[it] = (keys[it] ?: 0) + 1 }
        }
        return Result(data.size, packets, syncErrors, streams, videoPid, codec?.let { videoName(it) }, pes.size, raiCount, keys)
    }

    /** Tipo de imagen clave al principio de un PES de vídeo, o null si no lo es. */
    private fun keyframeKind(codec: Int?, b: ByteArray, from: Int): String? {
        var sps = false
        var i = from
        while (i + 3 < b.size) {
            if (b[i].u() != 0 || b[i + 1].u() != 0 || b[i + 2].u() != 1) { i++; continue }
            val h = b[i + 3].u()
            when (codec) {
                0x1B -> when (h and 0x1F) {
                    5 -> return "IDR"
                    7 -> sps = true
                    6 -> if (sps && i + 4 < b.size && b[i + 4].u() == 6) return "recuperación"
                    1 -> return if (sps && firstISlice(b, i + 4)) "I sin IDR" else null
                }
                0x24 -> when (val t = (h shr 1) and 0x3F) {
                    in 16..18 -> return "BLA"
                    19, 20 -> return "IDR"
                    21 -> return "CRA"
                    in 0..9 -> return null
                    else -> if (t == 33) sps = true
                }
                0x01, 0x02 -> if (h == 0x00 && i + 5 < b.size) return if ((b[i + 5].u() shr 3) and 7 == 1) "I" else null
                else -> return null
            }
            i += 3
        }
        return null
    }

    private fun firstISlice(b: ByteArray, from: Int): Boolean {
        var bit = 0
        fun get(pos: Int): Int? = b.getOrNull(from + pos / 8)?.let { (it.u() shr (7 - pos % 8)) and 1 }
        fun ue(): Int? {
            var lz = 0
            while (true) { val v = get(bit) ?: return null; if (v == 1) break; lz++; bit++; if (lz > 16) return null }
            bit++
            var v = 0
            repeat(lz) { v = (v shl 1) or (get(bit) ?: return null); bit++ }
            return (1 shl lz) - 1 + v
        }
        return ue() == 0 && ue().let { it == 2 || it == 7 }
    }

    private fun Byte.u(): Int = toInt() and 0xFF
}
