package com.mivisor.tv.engine

/**
 * 3i · Fuentes «calientes»: pedidas al motor por adelantado (al dejar el foco sobre un canal, o la siguiente fuente
 * mientras la primera tarda) para que, al abrirlas, el motor ya tenga emisores y la imagen salga mucho antes.
 *
 * Como mucho [capacity] a la vez (la memoria y la red de un TV stick son limitadas): al calentar una más, se suelta la
 * más antigua. Las que pasan [ttlMs] sin usarse se sueltan solas. La que el reproductor está usando nunca se suelta
 * desde aquí ([take]).
 */
class WarmPool(private val capacity: Int, private val ttlMs: Long) {
    private val warm = LinkedHashMap<String, Long>()

    fun contains(cid: String): Boolean = cid in warm

    /** Calienta [cid] (o la refresca). Devuelve las que hay que soltar para no pasar de [capacity]. */
    @Synchronized
    fun add(cid: String, nowMs: Long): List<String> {
        warm.remove(cid)
        warm[cid] = nowMs
        val out = ArrayList<String>()
        while (warm.size > capacity) {
            val oldest = warm.keys.first()
            warm.remove(oldest)
            out += oldest
        }
        return out
    }

    /** Las que llevan [ttlMs] sin usarse: hay que soltarlas. */
    @Synchronized
    fun expired(nowMs: Long): List<String> {
        val out = warm.filter { nowMs - it.value >= ttlMs }.keys.toList()
        out.forEach { warm.remove(it) }
        return out
    }

    /** El reproductor se queda con [cid]: deja de ser cosa de la precarga (no se soltará desde aquí). */
    @Synchronized
    fun take(cid: String): Boolean = warm.remove(cid) != null

    @Synchronized
    fun drain(): List<String> = warm.keys.toList().also { warm.clear() }

    val size: Int @Synchronized get() = warm.size
}
