package com.mivisor.tv.catalog

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.CompetitionNames
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 3g · Competiciones que han ido llegando en la agenda, guardadas en disco (filesDir/competitions.json). No hay un
 * listado oficial: cada agenda nueva añade las que traiga y actualiza cuántos eventos tienen ahora. Las que llevan
 * [FORGET_MS] sin aparecer se olvidan (salvo que estén ocultas o seguidas: esas se conservan para no perder la elección).
 */
class CompetitionBook(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {

    data class Seen(val id: String, val name: String, val sport: String, val lastSeen: Long, val events: Int)

    @Volatile private var cache: List<Seen>? = null

    @Synchronized
    fun all(): List<Seen> = cache ?: read().also { cache = it }

    /** Añade/actualiza con los eventos de la agenda recién llegada. [keep] = ids que no se olvidan nunca. */
    @Synchronized
    fun update(events: List<AgendaEvent>, keep: Set<String> = emptySet()): List<Seen> {
        val now = clock()
        val map = LinkedHashMap<String, Seen>()
        all().forEach { map[it.id] = it.copy(events = 0) }
        for ((id, list) in events.groupBy { CompetitionNames.id(it) }) {
            val name = list.mapNotNull { it.competition?.trim()?.takeIf(String::isNotEmpty) }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: map[id]?.name ?: list.first().sport
            map[id] = Seen(id, name, list.first().sport, now, list.size)
        }
        val result = map.values.filter { it.id in keep || now - it.lastSeen <= FORGET_MS }
            .sortedWith(compareBy<Seen> { it.sport }.thenByDescending { it.events }.thenBy { it.name.lowercase() })
        cache = result
        write(result)
        return result
    }

    private fun read(): List<Seen> = runCatching {
        if (!file.isFile) return emptyList()
        JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("competitions").mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            Seen(id, o.get("name")?.asString ?: id, o.get("sport")?.asString ?: "", o.get("lastSeen")?.asLong ?: 0L, o.get("events")?.asInt ?: 0)
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<Seen>) {
        runCatching {
            val arr = JsonArray()
            list.forEach { s ->
                arr.add(JsonObject().apply {
                    addProperty("id", s.id); addProperty("name", s.name); addProperty("sport", s.sport)
                    addProperty("lastSeen", s.lastSeen); addProperty("events", s.events)
                })
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(JsonObject().apply { add("competitions", arr) }.toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    companion object {
        val FORGET_MS: Long = TimeUnit.DAYS.toMillis(90)
    }
}
