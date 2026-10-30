package com.mivisor.tv.data

import com.mivisor.tv.diagnostics.Redactor
import com.mivisor.tv.storage.LogRow
import com.mivisor.tv.storage.MvDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Registro de la app: todo pasa por el [Redactor] ANTES de guardarse (en disco nunca hay códigos, IDs ni URLs). */
class LogBook(private val db: MvDatabase, private val scope: CoroutineScope, val redactor: Redactor) {
    fun add(category: String, text: String) {
        val clean = redactor.redact(text).take(400)
        scope.launch { runCatching { db.addLog(category, clean) } }
    }

    fun recent(limit: Int = 200): List<LogRow> = runCatching { db.logs(limit) }.getOrDefault(emptyList())

    fun clear() {
        scope.launch { runCatching { db.clearLogs() } }
    }
}
