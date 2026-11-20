package dev.dazaike.cadence.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What Cadence is doing right now, in plain words ("Syncing Samsung Health with your watch…",
 * "Syncing to cloud…"), for the status line at the top of the app. Each piece of work owns one
 * [id] and replaces its own message as it progresses; the line shows them all, oldest first.
 */
class StatusHub {
    data class Entry(val id: String, val text: String)

    private val _active = MutableStateFlow<List<Entry>>(emptyList())
    val active: StateFlow<List<Entry>> = _active.asStateFlow()

    fun set(id: String, text: String) {
        _active.update { current ->
            if (current.any { it.id == id }) current.map { if (it.id == id) Entry(id, text) else it }
            else current + Entry(id, text)
        }
    }

    fun clear(id: String) {
        _active.update { current -> current.filterNot { it.id == id } }
    }

    companion object {
        const val WAKE = "wake"
        const val LOAD = "load"
        const val CLOUD = "cloud"
    }
}
