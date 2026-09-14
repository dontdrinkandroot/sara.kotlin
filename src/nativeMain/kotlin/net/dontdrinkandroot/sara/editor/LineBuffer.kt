package net.dontdrinkandroot.sara.editor

/**
 * Immutable text buffer with a cursor, edited by [LineEditor]. The text may contain
 * newlines (multiline pastes, Alt+Enter).
 */
data class LineBuffer(val chars: String = "", val cursor: Int = 0) {

    init {
        require(cursor in 0..chars.length) { "Cursor $cursor is outside the buffer of length ${chars.length}" }
    }

    /** Inserts [text] at the cursor; the cursor ends up after the inserted text. */
    fun insert(text: String): LineBuffer =
        LineBuffer(
            chars.substring(0, cursor) + text + chars.substring(cursor),
            cursor + text.length
        )

    /** Deletes the character before the cursor; no-op at the start. */
    fun deleteBackward(): LineBuffer =
        if (cursor == 0) this else LineBuffer(chars.removeRange(cursor - 1, cursor), cursor - 1)

    /** Deletes the character at the cursor; no-op at the end. */
    fun deleteForward(): LineBuffer =
        if (cursor == chars.length) this else LineBuffer(chars.removeRange(cursor, cursor + 1), cursor)

    fun moveLeft(): LineBuffer = if (cursor == 0) this else copy(cursor = cursor - 1)

    fun moveRight(): LineBuffer = if (cursor == chars.length) this else copy(cursor = cursor + 1)

    fun moveToStart(): LineBuffer = copy(cursor = 0)

    fun moveToEnd(): LineBuffer = copy(cursor = chars.length)
}