package com.mivisor.tv.catalog.tv

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Búsqueda tolerante a erratas (la del prototipo de la fase 2): «betys» encuentra Betis, «atleti» al Atlético.
 * Cada palabra buscada debe casar con alguna palabra del texto: por prefijo o, si tiene 4 letras o más, con una
 * distancia de edición de 1 (2 si tiene 7 o más), contando las transposiciones.
 */
object TvSearch {
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9ñ+ ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    /** 0 = no casa; cuanto mayor, mejor. */
    fun score(query: String, text: String): Int {
        val qs = norm(query).split(' ').filter { it.isNotEmpty() }
        if (qs.isEmpty()) return 0
        val ts = norm(text).split(' ').filter { it.isNotEmpty() }
        var total = 0
        for (q in qs) {
            var best = 0
            for (t in ts) best = max(best, token(q, t))
            if (best == 0) return 0
            total += best
        }
        return total
    }

    fun channels(query: String, all: List<TvChannel>, limit: Int = 24): List<TvChannel> =
        all.map { it to score(query, it.name + " " + (it.brandName ?: "")) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit).map { it.first }

    private fun token(q: String, t: String): Int {
        if (t.startsWith(q)) return 2
        if (q.length >= 4) {
            val lim = if (q.length >= 7) 2 else 1
            if (distance(q, t.take(q.length)) <= lim || distance(q, t) <= lim) return 1
        }
        return 0
    }

    /** Damerau-Levenshtein con transposiciones adyacentes. */
    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
}
