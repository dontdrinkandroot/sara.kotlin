package net.dontdrinkandroot.sara.session

import kotlinx.serialization.Serializable
import net.dontdrinkandroot.sara.Message
import net.dontdrinkandroot.sara.Mode

/**
 * Serializable snapshot of one SARA session: the full conversation [messages] (verbatim,
 * including system, assistant-with-tool_calls and tool messages), the [mode] name active at
 * the time of the last change, and the ISO 8601 [savedAt] timestamp of that moment.
 *
 * Restoring replays the saved state verbatim (no fresh system prompt, no injected
 * mode-switch message) so the conversation is indistinguishable from the live session.
 * [mode] is stored as its raw name so unknown values (hand-edited or future format) map
 * back via [savedModeOrDefault] instead of failing deserialization.
 */
@Serializable
data class SavedSession(
    val version: Int = CURRENT_VERSION,
    val savedAt: String,
    val mode: String,
    val messages: List<Message>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Maps a stored mode name back to a [Mode]; unknown values fall back to [Mode.EXEC] so a
 * hand-edited or future-format file never blocks startup.
 */
fun savedModeOrDefault(raw: String?): Mode =
    Mode.entries.firstOrNull { it.name == raw } ?: Mode.EXEC
