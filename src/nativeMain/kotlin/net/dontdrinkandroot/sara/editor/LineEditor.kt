package net.dontdrinkandroot.sara.editor

/**
 * The pure editing state machine: maps an [EditEvent] to the next [EditResult].
 *
 * Stateless and side-effect free so the specification is directly testable; input
 * adapters (terminal, scripted) own event sourcing and rendering.
 */
object LineEditor {

    fun apply(event: EditEvent, buffer: LineBuffer): EditResult = when (event) {
        is EditEvent.InsertText -> EditResult.Continue(buffer.insert(event.text))
        EditEvent.InsertNewline -> EditResult.Continue(buffer.insert("\n"))

        EditEvent.SubmitLine ->
            if (buffer.chars.endsWith("\\")) {
                // Trailing backslash continues the input on the next line (bash convention);
                // the backslash itself is dropped.
                val withoutBackslash = buffer.chars.dropLast(1)
                val clampedCursor = minOf(buffer.cursor, withoutBackslash.length)
                EditResult.Continue(LineBuffer(withoutBackslash, clampedCursor).insert("\n"))
            } else {
                EditResult.Submit(buffer.chars)
            }

        EditEvent.Backspace -> EditResult.Continue(buffer.deleteBackward())
        EditEvent.DeleteForward -> EditResult.Continue(buffer.deleteForward())
        EditEvent.MoveLeft -> EditResult.Continue(buffer.moveLeft())
        EditEvent.MoveRight -> EditResult.Continue(buffer.moveRight())
        EditEvent.MoveHome -> EditResult.Continue(buffer.moveToStart())
        EditEvent.MoveEnd -> EditResult.Continue(buffer.moveToEnd())

        EditEvent.Interrupt -> EditResult.Abort

        // Ctrl+D: end of input on an empty buffer, delete-forward otherwise (bash convention).
        EditEvent.Eof ->
            if (buffer.chars.isEmpty()) EditResult.Eof else EditResult.Continue(buffer.deleteForward())

        // Paste markers delimit assembly in the input adapters; they never change the buffer.
        EditEvent.PasteStart, EditEvent.PasteEnd -> EditResult.Continue(buffer)
    }
}