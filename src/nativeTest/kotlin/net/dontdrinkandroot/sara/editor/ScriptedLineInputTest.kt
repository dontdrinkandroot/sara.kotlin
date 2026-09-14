package net.dontdrinkandroot.sara.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScriptedLineInputTest {

    @Test
    fun linesAreSubmittedVerbatim() {
        val inputs = mutableListOf("first", "second")
        val input = ScriptedLineInput { inputs.removeAt(0) }

        val first = input.readSubmission()
        assertIs<LineReadResult.Submitted>(first)
        assertEquals("first", first.text)
        val second = input.readSubmission()
        assertIs<LineReadResult.Submitted>(second)
        assertEquals("second", second.text)
    }

    @Test
    fun multilineLineIsSubmittedWithNewlines() {
        val input = ScriptedLineInput { "a\nb\nc" }
        val result = input.readSubmission()
        assertIs<LineReadResult.Submitted>(result)
        assertEquals("a\nb\nc", result.text)
    }

    @Test
    fun emptyLineMeansEof() {
        val input = ScriptedLineInput { "" }
        assertIs<LineReadResult.Eof>(input.readSubmission())
    }

    @Test
    fun nullMeansEof() {
        val input = ScriptedLineInput { null }
        assertIs<LineReadResult.Eof>(input.readSubmission())
    }
}