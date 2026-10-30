package com.mivisor.tv.diagnostics

/** Hechos del aparato y de los servicios para el informe (los reúne la app; aquí solo se da formato). */
data class ReportFacts(
    val app: String,
    val createdAt: String,
    val device: String,
    val network: String,
    val engine: String,
    val ace: String,
    val warp: String,
    val providers: String,
    val agenda: String,
    val quarantined: Int,
    val memory: String,
    val storage: String
)

/**
 * Informe de diagnóstico (3d). Pasa SIEMPRE por el [Redactor] antes de salir de la tele: sin códigos, Content IDs
 * completos, URLs ni IPs públicas. Se puede ver en la tele, abrir en el móvil por la red de casa, pasar al móvil
 * sin red (en varios QR) o compartir con otra app.
 */
object DiagnosticReport {
    fun build(f: ReportFacts, recent: List<String>, redactor: Redactor): String {
        val text = buildString {
            appendLine("${f.app} · informe del ${f.createdAt}")
            appendLine("Aparato: ${f.device}")
            appendLine("Red: ${f.network}")
            appendLine("Motor: ${f.engine}")
            appendLine("Ace Stream: ${f.ace}")
            appendLine("WARP: ${f.warp}")
            appendLine("Proveedores: ${f.providers}")
            appendLine("Agenda: ${f.agenda}")
            appendLine()
            recent.forEach { appendLine(it) }
            if (recent.isNotEmpty()) appendLine()
            append("Fuentes en cuarentena: ${f.quarantined} · Memoria libre: ${f.memory} · Almacenamiento libre: ${f.storage}")
        }
        return redactor.redact(text)
    }

    /**
     * Partes para pasar el informe al móvil SIN red: cada QR lleva «MiVisor TV · parte i/n» y un trozo.
     * [partChars] se elige para que cada QR se lea bien en la pantalla (≈ 700 caracteres).
     */
    fun parts(text: String, partChars: Int = 700): List<String> {
        val chunks = text.chunked(partChars).ifEmpty { listOf("") }
        return chunks.mapIndexed { i, c -> "MiVisor TV · parte ${i + 1}/${chunks.size}\n$c" }
    }
}
