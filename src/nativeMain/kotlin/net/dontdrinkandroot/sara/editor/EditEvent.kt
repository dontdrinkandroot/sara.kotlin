package net.dontdrinkandroot.sara.editor

/**
 * A single editing event processed by [LineEditor].
 *
 * The event model is intentionally decoupled from any terminal library so the editing
 * semantics can be tested without Mordant; adapters map raw input events to these.
 */
sealed interface EditEvent {
    /** Inserts arbitrary text at the cursor (typed characters or an assembled paste). */
    data class InsertText(val text: String) : EditEvent

    /** Inserts a line break at the cursor without submitting. */
    data object InsertNewline : EditEvent

    /** Enter: submits the buffer, unless it ends with a `\` continuation. */
    data object SubmitLine : EditEvent

    data object Backspace : EditEvent
    data object DeleteForward : EditEvent
    data object MoveLeft : EditEvent
    data object MoveRight : EditEvent
    data object MoveHome : EditEvent
    data object MoveEnd : EditEvent

    /** Ctrl+C: abandon the current input. */
    data object Interrupt : EditEvent

    /** Ctrl+D on an empty buffer: end of input. */
    data object Eof : EditEvent

    /** Bracketed paste markers; handled by the input adapters, no-ops for the editor. */
    data object PasteStart : EditEvent
    data object PasteEnd : EditEvent
}