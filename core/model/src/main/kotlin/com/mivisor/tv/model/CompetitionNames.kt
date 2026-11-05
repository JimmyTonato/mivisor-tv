package com.mivisor.tv.model

import java.text.Normalizer

/**
 * 3g · Competición REAL de un evento, tal como la trae la agenda («Copa Libertadores», «Liga MX»…), para que el
 * usuario elija cuáles ver (Ajustes › Competiciones) y siga las que quiera aunque la app no las conozca de antemano.
 *
 * Clave estable: el nombre en minúsculas, sin tildes ni signos («Copa Libertadores» → "copa libertadores"). Si el
 * evento no trae competición se usa el deporte ("deporte:futbol"), para poder ocultar también esos eventos sueltos.
 * En preferencias y rutas va con el prefijo [PREFIX] para no confundirse con las competiciones fijas ([Competition]).
 */
object CompetitionNames {
    const val PREFIX = "raw:"

    fun key(event: AgendaEvent): String = event.competition?.let { keyOf(it) }?.takeIf { it.isNotEmpty() } ?: "deporte:${event.sport}"

    fun keyOf(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
            .lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()

    /** Id para rutas y preferencias: "raw:copa libertadores". */
    fun id(event: AgendaEvent): String = PREFIX + key(event)

    fun isRaw(id: String?): Boolean = id?.startsWith(PREFIX) == true

    /** ¿El evento es de esta competición? [id] puede ser fija (LALIGA) o real (raw:…). */
    fun matches(id: String, event: AgendaEvent): Boolean =
        if (isRaw(id)) PREFIX + key(event) == id else Competition.byName(id)?.let { Competition.of(event) == it } == true
}
