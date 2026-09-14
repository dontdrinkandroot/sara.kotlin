package net.dontdrinkandroot.sara.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class LineEditorTest {

    private val empty = LineBuffer()

    private fun continueResult(event: EditEvent, buffer: LineBuffer): LineBuffer {
        val result = LineEditor.apply(event, buffer)
        assertIs<EditResult.Continue>(result)
        return result.buffer
    }

    @Test
    fun typedTextAccumulatesAndSubmitsOnEnter() {
        var buffer = continueResult(EditEvent.InsertText("hello"), empty)
        buffer = continueResult(EditEvent.InsertText(" world"), buffer)
        val result = LineEditor.apply(EditEvent.SubmitLine, buffer)
        assertIs<EditResult.Submit>(result)
        assertEquals("hello world", result.text)
    }

    @Test
    fun submitEmptyBufferSubmitsEmptyString() {
        val result = LineEditor.apply(EditEvent.SubmitLine, empty)
        assertIs<EditResult.Submit>(result)
        assertEquals("", result.text)
    }

    @Test
    fun backspaceRemovesBeforeCursor() {
        var buffer = continueResult(EditEvent.InsertText("hello"), empty)
        buffer = continueResult(EditEvent.Backspace, buffer)
        assertEquals("hell", buffer.chars)
        assertEquals(4, buffer.cursor)
    }

    @Test
    fun deleteForwardRemovesAtCursor() {
        var buffer = continueResult(EditEvent.InsertText("hello"), empty)
        buffer = continueResult(EditEvent.MoveHome, buffer)
        buffer = continueResult(EditEvent.DeleteForward, buffer)
        assertEquals("ello", buffer.chars)
        assertEquals(0, buffer.cursor)
    }

    @Test
    fun movesHomeAndEnd() {
        var buffer = continueResult(EditEvent.InsertText("abc"), empty)
        buffer = continueResult(EditEvent.MoveHome, buffer)
        assertEquals(0, buffer.cursor)
        buffer = continueResult(EditEvent.MoveEnd, buffer)
        assertEquals(3, buffer.cursor)
    }

    @Test
    fun insertNewlineEmbedsNewline() {
        val buffer = continueResult(EditEvent.InsertNewline, continueResult(EditEvent.InsertText("a"), empty))
        assertEquals("a\n", buffer.chars)
    }

    @Test
    fun trailingBackslashContinuesOnNextLine() {
        // "abc\" + Enter -> newline inserted, backslash dropped
        var buffer = continueResult(EditEvent.InsertText("abc\\"), empty)
        buffer = continueResult(EditEvent.SubmitLine, buffer)
        assertEquals("abc\n", buffer.chars)
        assertEquals(4, buffer.cursor)

        // The continuation line can then be completed and submitted
        buffer = continueResult(EditEvent.InsertText("def"), buffer)
        val result = LineEditor.apply(EditEvent.SubmitLine, buffer)
        assertIs<EditResult.Submit>(result)
        assertEquals("abc\ndef", result.text)
    }

    @Test
    fun interruptedAborts() {
        val result = LineEditor.apply(EditEvent.Interrupt, LineBuffer("partial", 7))
        assertIs<EditResult.Abort>(result)
    }

    @Test
    fun eofOnEmptyBufferEndsInput() {
        val result = LineEditor.apply(EditEvent.Eof, empty)
        assertIs<EditResult.Eof>(result)
    }

    @Test
    fun eofOnNonEmptyBufferDeletesForward() {
        // Ctrl+D with content behaves like delete-forward (bash convention)
        var buffer = continueResult(EditEvent.InsertText("ab"), empty)
        buffer = continueResult(EditEvent.MoveHome, buffer)
        buffer = continueResult(EditEvent.Eof, buffer)
        assertEquals("b", buffer.chars)
        assertEquals(0, buffer.cursor)

        // At the end of the buffer it is a no-op, NOT an EOF
        val atEnd = continueResult(EditEvent.Eof, LineBuffer("ab", 2))
        assertEquals("ab", atEnd.chars)
    }

    @Test
    fun pasteMarkersAreNoOpsOnTheBuffer() {
        var buffer = continueResult(EditEvent.PasteStart, empty)
        assertSame(empty, buffer)
        buffer = continueResult(EditEvent.PasteEnd, buffer)
        assertSame(empty, buffer)
    }

    @Test
    fun pasteEventInsertsTextVerbatim() {
        var buffer = continueResult(EditEvent.PasteStart, empty)
        buffer = continueResult(EditEvent.InsertText("line1\nline2\n"), buffer)
        buffer = continueResult(EditEvent.PasteEnd, buffer)
        assertEquals("line1\nline2\n", buffer.chars)
        assertEquals(12, buffer.cursor)
    }
}