package net.dontdrinkandroot.sara.editor

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.isCtrlC

/** Mordant's key names for the bracketed paste markers (POSIX parser). */
internal const val PASTE_START_KEY = "PasteStart"
internal const val PASTE_END_KEY = "PasteEnd"

/**
 * Pure mapping from a Mordant [KeyboardEvent] to an [EditEvent]; unknown or unhandled
 * keys map to null so the caller can skip them.
 *
 * Key names follow the MDN convention Mordant uses, e.g. `ArrowLeft`, `Home`, `Enter`.
 * The bracketed paste markers `ESC[200~`/`ESC[201~` are reported by Mordant's POSIX
 * parser as the keys [PASTE_START_KEY]/[PASTE_END_KEY]; between them every key is
 * reported as a plain character (line breaks included), so the paste assembles as text.
 */
internal fun keyEventToEditEvent(event: KeyboardEvent): EditEvent? = when {
    event.isCtrlC -> EditEvent.Interrupt

    event.key == "Enter" && !event.alt && !event.ctrl && !event.shift -> EditEvent.SubmitLine
    event.key == "Enter" -> EditEvent.InsertNewline

    event.key == "Backspace" && !event.ctrl -> EditEvent.Backspace
    event.key == "Delete" && !event.ctrl -> EditEvent.DeleteForward

    event.key == "ArrowLeft" && !event.alt -> EditEvent.MoveLeft
    event.key == "ArrowRight" && !event.alt -> EditEvent.MoveRight
    event.key == "Home" && !event.alt -> EditEvent.MoveHome
    event.key == "End" && !event.alt -> EditEvent.MoveEnd

    event.key == "d" && event.ctrl && !event.alt -> EditEvent.Eof

    // Raw line-break characters (e.g. inside a bracketed paste) insert a newline.
    event.key == "\n" || event.key == "\r" -> EditEvent.InsertNewline

    event.key == PASTE_START_KEY -> EditEvent.PasteStart
    event.key == PASTE_END_KEY -> EditEvent.PasteEnd

    event.key.length == 1 && !event.ctrl && !event.alt -> EditEvent.InsertText(event.key)

    else -> null
}

/**
 * The raw-mode prompt: opens a [RawEventReader] per submission, maps the Mordant input
 * events to edit events and feeds them to [LineEditor] until a submission, abort, or EOF.
 *
 * [readerFactory] abstracts opening raw mode for testability; [keyMapper] maps a Mordant
 * key event to an [EditEvent] (injected so tests can drive the state machine without
 * relying on the platform's reported key names). On Ctrl+C the editor aborts and
 * [raiseInterrupt] re-raises the signal so [SignalInterruptSource] semantics
 * (turn cancellation, second-Ctrl+C force exit) stay in effect.
 *
 * Rendering goes through the injected [rendererFactory]; a fresh renderer per submission
 * erases exactly the rows it drew, so multiline buffers redraw without leaving stale
 * text behind. Raw mode does not echo input itself.
 */
class MordantLineInput(
    private val readerFactory: () -> RawEventReader?,
    private val keyMapper: (KeyboardEvent) -> EditEvent? = ::keyEventToEditEvent,
    private val rendererFactory: () -> LineRenderer = { PromptRenderer(NoOpRenderTarget) },
    private val raiseInterrupt: () -> Unit,
) : LineInput {

    override fun readSubmission(): LineReadResult {
        val reader = readerFactory() ?: return LineReadResult.Eof
        val renderer = rendererFactory()
        try {
            var buffer = LineBuffer()
            var inPaste = false
            renderer.render(buffer)
            while (true) {
                val event = reader.readEvent() ?: return LineReadResult.Eof
                var editEvent = when (event) {
                    is KeyboardEvent -> keyMapper(event)
                    else -> null
                } ?: continue

                if (editEvent == EditEvent.PasteStart) inPaste = true
                if (editEvent == EditEvent.PasteEnd) inPaste = false
                // Inside a bracketed paste a pasted line break ("Enter" key) must never
                // submit the input; it is inserted as a newline instead.
                if (inPaste && editEvent == EditEvent.SubmitLine) editEvent = EditEvent.InsertNewline

                when (val result = LineEditor.apply(editEvent, buffer)) {
                    is EditResult.Continue -> {
                        buffer = result.buffer
                        renderer.render(buffer)
                    }

                    is EditResult.Submit -> {
                        renderer.finish()
                        return LineReadResult.Submitted(result.text)
                    }

                    EditResult.Abort -> {
                        renderer.finish()
                        raiseInterrupt()
                        return LineReadResult.Aborted
                    }

                    EditResult.Eof -> {
                        renderer.finish()
                        return LineReadResult.Eof
                    }
                }
            }
        } finally {
            reader.close()
        }
    }

    private companion object {
        /** Used in tests/headless contexts where rendering is a no-op. */
        val NoOpRenderTarget = object : RenderTarget {
            override fun moveUp(count: Int) = Unit
            override fun moveDown(count: Int) = Unit
            override fun moveRight(count: Int) = Unit
            override fun moveToStartOfLine() = Unit
            override fun moveToNextLine() = Unit
            override fun clearLine() = Unit
            override fun print(text: String) = Unit
        }
    }
}