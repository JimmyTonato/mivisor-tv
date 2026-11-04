package com.mivisor.tv.catalog.logos

import com.google.gson.JsonParser
import java.net.URLEncoder

/**
 * Logos de canales de picons_dobleM (https://github.com/davidmuma/picons_dobleM), carpeta `icon/`: un PNG por canal
 * con su nombre («M+ LaLiga.png», «DAZN 1.png»…). La lista de archivos se pide a la API de GitHub (dos peticiones:
 * la raíz para saber qué árbol es `icon/` y ese árbol) como mucho una vez por semana; las imágenes se cargan de
 * raw.githubusercontent.com solo cuando se ven. El nombre del archivo se empareja con el canal con la MISMA
 * normalización que agrupa los canales (sin calidad, M+ = Movistar…).
 */
object Picons {
    const val REPO = "davidmuma/picons_dobleM"
    const val BRANCH = "master"
    const val DIR = "icon"

    const val ROOT_TREE_URL = "https://api.github.com/repos/$REPO/git/trees/$BRANCH"
    fun treeUrl(sha: String) = "https://api.github.com/repos/$REPO/git/trees/$sha"

    /** SHA del árbol de `icon/` en la respuesta de la raíz, o null. */
    fun iconTreeSha(rootBody: String): String? = runCatching {
        JsonParser.parseString(rootBody).asJsonObject.getAsJsonArray("tree")
            .map { it.asJsonObject }
            .firstOrNull { it.get("path")?.asString == DIR && it.get("type")?.asString == "tree" }
            ?.get("sha")?.asString
    }.getOrNull()

    /** Nombres de los PNG del árbol de `icon/`. */
    fun pngNames(treeBody: String): List<String> = runCatching {
        JsonParser.parseString(treeBody).asJsonObject.getAsJsonArray("tree")
            .map { it.asJsonObject }
            .filter { it.get("type")?.asString == "blob" }
            .mapNotNull { it.get("path")?.asString }
            .filter { it.endsWith(".png", ignoreCase = true) && '/' !in it }
    }.getOrDefault(emptyList())

    /** URL directa del PNG («M+ LaLiga.png» → …/icon/M%2B%20LaLiga.png). */
    fun url(fileName: String): String =
        "https://raw.githubusercontent.com/$REPO/$BRANCH/$DIR/" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")

    /** Entradas de logo (prioridad máxima) para el resolvedor. */
    fun entries(names: List<String>): List<LogoEntry> =
        names.map { LogoEntry(it.substringBeforeLast('.'), url(it), LogoSource.PICONS) }
}
