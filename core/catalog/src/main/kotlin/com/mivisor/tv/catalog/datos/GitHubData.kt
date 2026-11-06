package com.mivisor.tv.catalog.datos

/**
 * 3j · Ramas de mivisor-datos. La agenda está en `main`; los banners en `banners` y el catálogo de proveedores en
 * `providers`. Las direcciones se sacan de la de la agenda (la de Ajustes si se cambió), igual que hacemos con ella.
 */
object GitHubData {
    const val DEFAULT_REPO = "https://raw.githubusercontent.com/JimmyTonato/mivisor-datos"
    private val RAW = Regex("""^(https://raw\.githubusercontent\.com/[^/]+/[^/]+)/(?:refs/heads/)?[^/]+/.+$""")

    /** Raíz del repositorio (sin rama) a partir de la URL de la agenda; si no es de GitHub, la de por defecto. */
    fun repoOf(agendaUrl: String?): String = agendaUrl?.trim()?.let { RAW.find(it)?.groupValues?.get(1) } ?: DEFAULT_REPO

    fun branchUrl(agendaUrl: String?, branch: String, path: String = ""): String =
        repoOf(agendaUrl) + "/" + branch + "/" + path.trimStart('/')

    fun bannersBase(agendaUrl: String?) = branchUrl(agendaUrl, "banners")
    fun bannersManifest(agendaUrl: String?) = branchUrl(agendaUrl, "banners", "manifest.json")
    fun providersCatalog(agendaUrl: String?) = branchUrl(agendaUrl, "providers", "proveedores.json")
}
