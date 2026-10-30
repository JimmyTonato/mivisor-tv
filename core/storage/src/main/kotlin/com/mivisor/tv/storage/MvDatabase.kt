package com.mivisor.tv.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local de MiVisor TV (SQLite del sistema; sin Room para no añadir KSP).
 * Todo se llama desde Dispatchers.IO. Nada de esto sale del aparato (allowBackup=false).
 */
class MvDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE provider(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, code TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL, last_ok INTEGER NOT NULL DEFAULT 0, channels INTEGER NOT NULL DEFAULT 0,
                last_error TEXT)"""
        )
        db.execSQL("CREATE TABLE favorite(channel_id TEXT PRIMARY KEY, name TEXT NOT NULL, added_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history(channel_id TEXT PRIMARY KEY, name TEXT NOT NULL, played_at INTEGER NOT NULL, seconds INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE source_health(cid TEXT PRIMARY KEY, json TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE channel_memory(channel_id TEXT PRIMARY KEY, cid TEXT NOT NULL, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE log(id INTEGER PRIMARY KEY AUTOINCREMENT, t INTEGER NOT NULL, cat TEXT NOT NULL, text TEXT NOT NULL)")
        createReminders(db)
    }

    /** Migraciones sin borrar datos: instalar encima conserva proveedores, favoritos, historial y fiabilidad. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createReminders(db)          // 3c: recordatorios
    }

    private fun createReminders(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS reminder(
                event_id TEXT PRIMARY KEY, title TEXT NOT NULL, start INTEGER NOT NULL, sport TEXT NOT NULL,
                competition TEXT, channels TEXT NOT NULL, notified INTEGER NOT NULL DEFAULT 0)"""
        )
    }

    // ------------------------------------------------------------------ recordatorios (3c)

    fun reminders(): List<ReminderRow> =
        readableDatabase.query("reminder", null, null, null, null, null, "start ASC").use { c ->
            c.rows { ReminderRow(str("event_id"), str("title"), long("start"), str("sport"), strOrNull("competition"), str("channels"), int("notified") != 0) }
        }

    fun saveReminder(r: ReminderRow) {
        writableDatabase.insertWithOnConflict("reminder", null, ContentValues().apply {
            put("event_id", r.eventId); put("title", r.title); put("start", r.start); put("sport", r.sport)
            put("competition", r.competition); put("channels", r.channels); put("notified", if (r.notified) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteReminder(eventId: String) {
        writableDatabase.delete("reminder", "event_id = ?", arrayOf(eventId))
    }

    fun markReminderNotified(eventId: String) {
        writableDatabase.update("reminder", ContentValues().apply { put("notified", 1) }, "event_id = ?", arrayOf(eventId))
    }

    // ------------------------------------------------------------------ proveedores

    fun providers(): List<ProviderRow> = readableDatabase.query(
        "provider", null, null, null, null, null, "position ASC, added_at ASC"
    ).use { c -> c.rows { ProviderRow(str("id"), str("name"), str("code"), long("added_at"), long("last_ok"), int("channels"), strOrNull("last_error"), int("position")) } }

    fun upsertProvider(p: ProviderRow) {
        writableDatabase.insertWithOnConflict("provider", null, ContentValues().apply {
            put("id", p.id); put("name", p.name); put("code", p.sealedCode); put("position", p.position)
            put("added_at", p.addedAt); put("last_ok", p.lastOk); put("channels", p.channels); put("last_error", p.lastError)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun providerResult(id: String, okAt: Long?, channels: Int?, error: String?) {
        writableDatabase.update("provider", ContentValues().apply {
            if (okAt != null) put("last_ok", okAt)
            if (channels != null) put("channels", channels)
            put("last_error", error)
        }, "id = ?", arrayOf(id))
    }

    fun renameProvider(id: String, name: String) {
        writableDatabase.update("provider", ContentValues().apply { put("name", name) }, "id = ?", arrayOf(id))
    }

    fun deleteProvider(id: String) {
        writableDatabase.delete("provider", "id = ?", arrayOf(id))
    }

    // ------------------------------------------------------------------ Mi lista

    fun favorites(): List<Pair<String, String>> =
        readableDatabase.query("favorite", null, null, null, null, null, "added_at DESC").use { c -> c.rows { str("channel_id") to str("name") } }

    fun setFavorite(channelId: String, name: String, on: Boolean) {
        if (on) writableDatabase.insertWithOnConflict("favorite", null, ContentValues().apply {
            put("channel_id", channelId); put("name", name); put("added_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        else writableDatabase.delete("favorite", "channel_id = ?", arrayOf(channelId))
    }

    fun history(limit: Int = 30): List<HistoryRow> =
        readableDatabase.query("history", null, null, null, null, null, "played_at DESC", limit.toString())
            .use { c -> c.rows { HistoryRow(str("channel_id"), str("name"), long("played_at"), long("seconds")) } }

    fun addHistory(channelId: String, name: String, seconds: Long) {
        val db = writableDatabase
        db.insertWithOnConflict("history", null, ContentValues().apply {
            put("channel_id", channelId); put("name", name); put("played_at", System.currentTimeMillis()); put("seconds", seconds)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL("DELETE FROM history WHERE channel_id NOT IN (SELECT channel_id FROM history ORDER BY played_at DESC LIMIT 60)")
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    // ------------------------------------------------------------------ fiabilidad

    fun healthJson(): Map<String, String> =
        readableDatabase.query("source_health", null, null, null, null, null, null).use { c -> c.rows { str("cid") to str("json") } }.toMap()

    fun saveHealth(rows: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for ((cid, json) in rows) {
                db.insertWithOnConflict("source_health", null, ContentValues().apply {
                    put("cid", cid); put("json", json); put("updated_at", now)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearHealth() {
        writableDatabase.delete("source_health", null, null)
        writableDatabase.delete("channel_memory", null, null)
    }

    fun rememberedSource(channelId: String): String? =
        readableDatabase.query("channel_memory", arrayOf("cid"), "channel_id = ?", arrayOf(channelId), null, null, null)
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun rememberSource(channelId: String, cid: String) {
        writableDatabase.insertWithOnConflict("channel_memory", null, ContentValues().apply {
            put("channel_id", channelId); put("cid", cid); put("at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ------------------------------------------------------------------ registro (ya limpio: lo pasa el Redactor antes)

    fun addLog(cat: String, text: String) {
        val db = writableDatabase
        db.insert("log", null, ContentValues().apply { put("t", System.currentTimeMillis()); put("cat", cat); put("text", text) })
        db.execSQL("DELETE FROM log WHERE id <= (SELECT MAX(id) FROM log) - $LOG_KEPT")
    }

    fun logs(limit: Int = 200): List<LogRow> =
        readableDatabase.query("log", null, null, null, null, null, "id DESC", limit.toString())
            .use { c -> c.rows { LogRow(long("t"), str("cat"), str("text")) } }

    fun clearLogs() {
        writableDatabase.delete("log", null, null)
    }

    private inline fun <T> Cursor.rows(read: Cursor.() -> T): List<T> {
        val out = ArrayList<T>(count.coerceAtLeast(0))
        while (moveToNext()) out += read()
        return out
    }

    private fun Cursor.str(col: String): String = getString(getColumnIndexOrThrow(col)) ?: ""
    private fun Cursor.strOrNull(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))

    companion object {
        const val NAME = "mivisor-tv.db"
        /** 1 = 3a/3b · 2 = 3c (recordatorios). */
        const val VERSION = 2
        const val LOG_KEPT = 500
    }
}

/** Un proveedor guardado. [sealedCode] es el código cifrado con [CodeVault]; nunca se muestra. */
class ProviderRow(
    val id: String,
    val name: String,
    val sealedCode: String,
    val addedAt: Long,
    val lastOk: Long = 0,
    val channels: Int = 0,
    val lastError: String? = null,
    val position: Int = 0
)

class HistoryRow(val channelId: String, val name: String, val playedAt: Long, val seconds: Long)

class LogRow(val t: Long, val cat: String, val text: String)

/** Recordatorio guardado. [channels] = nombres de canal separados por «\n». */
class ReminderRow(
    val eventId: String,
    val title: String,
    val start: Long,
    val sport: String,
    val competition: String?,
    val channels: String,
    val notified: Boolean
)
