package net.dontdrinkandroot.sara.session

import net.dontdrinkandroot.sara.Message
import net.dontdrinkandroot.sara.Mode

/**
 * Outcome of loading the persisted session at startup.
 */
sealed interface SessionLoad {
    /** No saved session exists (fresh start, nothing to offer). */
    data object NoSession : SessionLoad

    /** A saved session was found and can be restored. */
    data class Found(val saved: SavedSession) : SessionLoad

    /**
     * A saved session file existed but was unreadable; it has been moved aside to
     * `session.json.bak` for manual inspection and the session starts fresh.
     */
    data object Corrupt : SessionLoad
}

/**
 * Persists the current session so it can be restored at the next startup.
 *
 * The store is written after every conversation change and survives clean exits; deleting
 * it marks the session as "start fresh". Injected into [net.dontdrinkandroot.sara.Sara] for
 * testability: production uses [FileSessionStore], tests an in-memory fake.
 */
interface SessionStore {
    fun load(): SessionLoad
    fun save(messages: List<Message>, mode: Mode)
    fun delete()
}
