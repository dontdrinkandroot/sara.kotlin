package net.dontdrinkandroot.sara.editor

/**
 * Enables bracketed paste mode (`ESC[?2004h`) on the terminal and disables it
 * (`ESC[?2004l`) on close. Terminals without support ignore the escape sequence, so this
 * is always safe to use.
 *
 * With bracketed paste enabled, the terminal wraps every paste in the markers that
 * Mordant's POSIX parser reports as the `PasteStart`/`PasteEnd` keys (see
 * [keyEventToEditEvent]), which keeps multiline pastes from being submitted line by line.
 */
class BracketedPasteMode(private val write: (String) -> Unit) : AutoCloseable {

    private var enabled = false

    fun enable() {
        if (enabled) return
        write("\u001b[?2004h")
        enabled = true
    }

    override fun close() {
        if (!enabled) return
        write("\u001b[?2004l")
        enabled = false
    }
}