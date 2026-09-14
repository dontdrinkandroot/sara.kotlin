package net.dontdrinkandroot.sara

import net.dontdrinkandroot.sara.session.SavedSession
import net.dontdrinkandroot.sara.session.SessionLoad
import net.dontdrinkandroot.sara.session.SessionStore

/**
 * In-memory [SessionStore] for tests. Keeps the last saved state so tests can both seed a
 * "previous session" (by calling [save]) and assert what was persisted during a run.
 */
class FakeSessionStore : SessionStore {

    var savedSession: SavedSession? = null
    var deleteCount: Int = 0
    var failOnSave: Boolean = false
    var corruptOnLoad: Boolean = false

    override fun load(): SessionLoad = when {
        corruptOnLoad -> SessionLoad.Corrupt
        savedSession != null -> SessionLoad.Found(savedSession!!)
        else -> SessionLoad.NoSession
    }

    override fun save(messages: List<Message>, mode: Mode) {
        if (failOnSave) throw RuntimeException("simulated save failure")
        savedSession = SavedSession(
            savedAt = "2026-01-01T00:00:00+00:00",
            mode = mode.name,
            messages = messages.toList(),
        )
    }

    override fun delete() {
        deleteCount++
        savedSession = null
    }
}
