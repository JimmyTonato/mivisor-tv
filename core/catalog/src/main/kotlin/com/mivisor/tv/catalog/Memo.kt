package com.mivisor.tv.catalog

/**
 * 3g · Memoria pequeña y acotada para funciones puras que se llaman miles de veces con las mismas entradas (el mismo
 * nombre de canal aparece en muchos proveedores y en cada recarga). Al pasar de [max] entradas se vacía entera:
 * más simple y barato que una LRU, y nunca crece sin límite en una tele con poca memoria. Segura entre hilos.
 */
class Memo<K : Any, V : Any>(private val max: Int) {
    private val map = HashMap<K, V>()

    fun get(key: K, compute: (K) -> V): V {
        synchronized(map) { map[key]?.let { return it } }
        val v = compute(key)
        synchronized(map) {
            if (map.size >= max) map.clear()
            map[key] = v
        }
        return v
    }
}
