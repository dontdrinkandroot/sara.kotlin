package net.dontdrinkandroot.sara.editor

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MordantLineInputTest {

    /** Records the rendered buffer states through the [LineRenderer] seam. */
    private class RecordingRenderer : LineRenderer {
        val rendered = StringBuilder()
        var finished = false

        override fun render(buffer: LineBuffer) {
            rendered.append(buffer.chars).append("|")
        }

        override fun finish() {
            finished = true
        }
    }

    private fun input(
        queue: MutableList<InputEvent>,
        renderer: RecordingRenderer,
        raiseInterrupt: () -> Unit = {},
    ): MordantLineInput = MordantLineInput(
        readerFactory = {
            object : RawEventReader {
                override fun readEvent(): InputEvent? =
                    if (queue.isEmpty()) null else queue.removeAt(0)

                override fun close() {}
            }
        },
        rendererFactory = { renderer },
        raiseInterrupt = raiseInterrupt,
    )

    private fun readLine(vararg events: Any?, raiseInterrupt: () -> Unit = {}): LineReadResult {
        val queue = events.map { it as InputEvent }.toMutableList()
        return input(queue, RecordingRenderer(), raiseInterrupt).readSubmission()
    }

    @Test
    fun typedKeysAndEnterSubmit() {
        val result = readLine(
            KeyboardEvent("h"),
            KeyboardEvent("i"),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("hi", result.text)
    }

    @Test
    fun bufferIsRenderedAfterEveryEdit() {
        val queue = mutableListOf(
            KeyboardEvent("h") as InputEvent,
            KeyboardEvent("i"),
            KeyboardEvent("Enter"),
        )
        val renderer = RecordingRenderer()
        input(queue, renderer).readSubmission()
        // Initial empty prompt + one render per edit; finish() was called on submit.
        assertEquals("|h|hi|", renderer.rendered.toString())
        assertEquals(true, renderer.finished)
    }

    @Test
    fun editingKeysShapeTheSubmission() {
        val result = readLine(
            KeyboardEvent("h"),
            KeyboardEvent("e"),
            KeyboardEvent("l"),
            KeyboardEvent("l"),
            KeyboardEvent("o"),
            KeyboardEvent("x"),
            KeyboardEvent("Backspace"),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("hello", result.text)
    }

    @Test
    fun altEnterBuildsMultilineSubmission() {
        val result = readLine(
            KeyboardEvent("a"),
            KeyboardEvent("Enter", alt = true),
            KeyboardEvent("b"),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("a\nb", result.text)
    }

    @Test
    fun pasteEventIsInsertedVerbatimWithoutSubmitting() {
        // Paste content arrives as per-character keys; the embedded line break must not
        // submit and becomes part of the buffer.
        val result = readLine(
            KeyboardEvent("a"),
            KeyboardEvent(PASTE_START_KEY),
            KeyboardEvent("l"),
            KeyboardEvent("i"),
            KeyboardEvent("n"),
            KeyboardEvent("e"),
            KeyboardEvent("\n"),
            KeyboardEvent(PASTE_END_KEY),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("aline\n", result.text)
    }

    @Test
    fun enterInsidePasteDoesNotSubmit() {
        // A pasted CR is reported as the "Enter" key; between the paste markers it must
        // insert a newline instead of submitting the partial input.
        val result = readLine(
            KeyboardEvent(PASTE_START_KEY),
            KeyboardEvent("l"),
            KeyboardEvent("i"),
            KeyboardEvent("Enter"),
            KeyboardEvent("n"),
            KeyboardEvent("e"),
            KeyboardEvent(PASTE_END_KEY),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("li\nne", result.text)
    }

    @Test
    fun interruptAbortsAndRaisesSignal() {
        var raised = false
        val result = readLine(
            KeyboardEvent("x"),
            KeyboardEvent("c", ctrl = true),
            raiseInterrupt = { raised = true },
        )
        assertIs<LineReadResult.Aborted>(result)
        assertEquals(true, raised)
    }

    @Test
    fun ctrlDOnEmptyBufferMeansEof() {
        val result = readLine(KeyboardEvent("d", ctrl = true))
        assertIs<LineReadResult.Eof>(result)
    }

    @Test
    fun ctrlDOnNonEmptyBufferDeletesForwardInstead() {
        // Cursor is at the end after typing: Ctrl+D is a no-op there (bash behavior),
        // so the typed text is submitted as-is.
        val result = readLine(
            KeyboardEvent("a"),
            KeyboardEvent("d", ctrl = true),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("a", result.text)
    }

    @Test
    fun ctrlDAtStartOfNonEmptyBufferDeletesFirstCharacter() {
        val result = readLine(
            KeyboardEvent("a"),
            KeyboardEvent("Home"),
            KeyboardEvent("d", ctrl = true),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        // "a" deleted, buffer empty, Enter submits an empty line
        assertEquals("", result.text)
    }

    @Test
    fun nullEventMeansEof() {
        val result = readLine()
        assertIs<LineReadResult.Eof>(result)
    }

    @Test
    fun nonInteractiveImmediatelyReportsEof() {
        val input = MordantLineInput(
            readerFactory = { null },
            rendererFactory = { RecordingRenderer() },
            raiseInterrupt = {},
        )
        assertIs<LineReadResult.Eof>(input.readSubmission())
    }

    @Test
    fun mouseEventsAreIgnored() {
        val result = readLine(
            MouseEvent(0, 0, true),
            KeyboardEvent("a"),
            KeyboardEvent("Enter"),
        )
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("a", result.text)
    }
}