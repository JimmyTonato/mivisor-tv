package com.mivisor.tv.catalog.biblioteca

import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 4f · Actualizar la Biblioteca sin rehacerlo todo (contrato de MiVisor Gold):
 *  1. Se baja manifest.json. Misma revisión que la guardada → nada.
 *  2. Copia guardada con revisión ≥ «cambios_desde» → se baja cambios.enc y se aplican solo las líneas con rev mayor.
 *  3. Sin copia, o más vieja que «cambios_desde» → se baja biblioteca.enc entera.
 *  4. La revisión nueva solo se guarda si todo salió bien; si algo falla, la copia anterior queda intacta.
 *
 * La caché de GitHub puede servir unos minutos un .enc más viejo que el manifest: si lo bajado no llega a la revisión
 * del manifest, NO se guarda y se reintenta en la siguiente comprobación ([Outcome.NotReady]).
 */
class BibliotecaSync(
    /** Carpeta raw de la rama: …/mivisor-datos/biblioteca (sin barra final). */
    private val baseUrl: () -> String,
    private val fetch: (url: String) -> ByteArray,
    private val clock: () -> Long = System::currentTimeMillis
) {
    sealed class Outcome {
        class Unchanged(val manifest: Manifest) : Outcome()
        class Updated(val result: Aplicado, val manifest: Manifest, val full: Boolean) : Outcome()
        class NotReady(val manifest: Manifest, val why: String) : Outcome()
        object WrongPassword : Outcome()
        class Failed(val why: String) : Outcome()
    }

    fun sync(current: Biblioteca?, password: CharArray): Outcome {
        val manifest = try {
            Manifest.parse(String(fetch(url("manifest.json")), Charsets.UTF_8))
                ?: return Outcome.Failed("manifest.json no es del formato ${Manifest.FORMATO}")
        } catch (e: Exception) {
            return Outcome.Failed("no se pudo leer manifest.json (${e.message ?: e.javaClass.simpleName})")
        }
        if (current != null && !current.isEmpty && current.revision == manifest.revision) return Outcome.Unchanged(manifest)
        return try {
            val since = manifest.cambiosDesde
            if (current != null && !current.isEmpty && since != null && current.revision >= since && current.revision < manifest.revision) {
                val text = BibliotecaCrypto.decrypt(fetch(url("cambios.enc")), password, manifest.iteraciones)
                val changes = BibliotecaParser.parseChanges(text)
                val maxRev = changes.maxOfOrNull { it.rev } ?: -1
                if (maxRev >= manifest.revision) {
                    return Outcome.Updated(BibliotecaParser.apply(current, changes, manifest.revision), manifest, full = false)
                }
                // cambios.enc aún es el de antes (caché de GitHub): se prueba con el fichero completo.
            }
            full(manifest, password)
        } catch (e: BibliotecaCrypto.WrongPasswordException) {
            Outcome.WrongPassword
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun full(manifest: Manifest, password: CharArray): Outcome {
        val text = BibliotecaCrypto.decrypt(fetch(url("biblioteca.enc")), password, manifest.iteraciones)
        val lib = BibliotecaParser.parseFull(text)
        if (lib.revision < manifest.revision) {
            return Outcome.NotReady(manifest, "GitHub aún sirve la revisión ${lib.revision} (la nueva es ${manifest.revision})")
        }
        return Outcome.Updated(Aplicado(lib, lib.canales.keys, lib.categorias.keys), manifest, full = true)
    }

    /** La caché de raw.githubusercontent dura unos minutos: un parámetro por minuto la evita sin machacar a GitHub. */
    private fun url(file: String) = "${baseUrl().trimEnd('/')}/$file?t=${clock() / 60_000}"
}

/**
 * 4f · Copia de la Biblioteca en el aparato (carpeta privada de la app): JSON Lines comprimido, con la revisión
 * aplicada en la primera línea. Se escribe en un temporal y se renombra: nunca queda a medias.
 */
class BibliotecaStore(private val dir: File) {
    private val file get() = File(dir, "biblioteca.jsonl.gz")

    fun load(): Biblioteca? = runCatching {
        if (!file.isFile) return null
        val text = GZIPInputStream(file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        BibliotecaParser.parseFull(text).takeIf { !it.isEmpty }
    }.getOrNull()

    fun save(lib: Biblioteca) {
        dir.mkdirs()
        val tmp = File(dir, "biblioteca.jsonl.gz.tmp")
        GZIPOutputStream(tmp.outputStream()).use { it.write(lib.toJsonLines().toByteArray(Charsets.UTF_8)) }
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw java.io.IOException("no se pudo guardar la Biblioteca") }
    }

    fun clear() {
        file.delete()
        File(dir, "biblioteca.jsonl.gz.tmp").delete()
    }
}
