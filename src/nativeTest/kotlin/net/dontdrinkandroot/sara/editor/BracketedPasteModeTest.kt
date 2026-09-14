package net.dontdrinkandroot.sara.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class BracketedPasteModeTest {

    @Test
    fun enableWritesTheEnableSequenceOnce() {
        val written = mutableListOf<String>()
        val mode = BracketedPasteMode { written.add(it) }

        mode.enable()
        mode.enable()

        assertEquals(listOf("\u001b[?2004h"), written)
    }

    @Test
    fun closeWritesTheDisableSequenceOnce() {
        val written = mutableListOf<String>()
        val mode = BracketedPasteMode { written.add(it) }

        mode.enable()
        mode.close()
        mode.close()

        assertEquals(listOf("\u001b[?2004h", "\u001b[?2004l"), written)
    }

    @Test
    fun closeWithoutEnableIsANoOp() {
        val written = mutableListOf<String>()
        val mode = BracketedPasteMode { written.add(it) }

        mode.close()

        assertEquals(emptyList(), written)
    }
}