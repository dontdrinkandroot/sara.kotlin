package net.dontdrinkandroot.sara.editor

/** The outcome of applying an [EditEvent] to a [LineBuffer]. */
sealed interface EditResult {
    /** The event was absorbed; editing continues with the returned buffer. */
    data class Continue(val buffer: LineBuffer) : EditResult

    /** The user submitted the buffer content (may span multiple lines). */
    data class Submit(val text: String) : EditResult

    /** The user abandoned the current input (Ctrl+C). */
    data object Abort : EditResult

    /** The user requested end of input (Ctrl+D on an empty buffer). */
    data object Eof : EditResult
}