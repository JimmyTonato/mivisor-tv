package com.mivisor.tv.data

import com.mivisor.tv.storage.HistoryRow
import com.mivisor.tv.storage.MvDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MyListState(
    /** channelId → nombre. */
    val favorites: List<Pair<String, String>> = emptyList(),
    val history: List<HistoryRow> = emptyList()
) {
    fun isFavorite(channelId: String) = favorites.any { it.first == channelId }
}

/** Favoritos e historial (en la base de datos local, por id de canal estable). */
class MyListRepository(private val db: MvDatabase, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(MyListState())
    val state: StateFlow<MyListState> = _state.asStateFlow()

    fun load() {
        scope.launch { reload() }
    }

    private suspend fun reload() {
        _state.value = withContext(Dispatchers.IO) {
            MyListState(runCatching { db.favorites() }.getOrDefault(emptyList()), runCatching { db.history() }.getOrDefault(emptyList()))
        }
    }

    fun toggleFavorite(channelId: String, name: String) {
        val on = !_state.value.isFavorite(channelId)
        scope.launch {
            withContext(Dispatchers.IO) { db.setFavorite(channelId, name, on) }
            reload()
        }
    }

    fun addHistory(channelId: String, name: String, seconds: Long) {
        scope.launch {
            withContext(Dispatchers.IO) { db.addHistory(channelId, name, seconds) }
            reload()
        }
    }

    fun clearHistory() {
        scope.launch {
            withContext(Dispatchers.IO) { db.clearHistory() }
            reload()
        }
    }
}
