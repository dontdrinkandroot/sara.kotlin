package net.dontdrinkandroot.sara.editor

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class KeyEventMappingTest {

    private fun mapped(event: KeyboardEvent) = keyEventToEditEvent(event)

    @Test
    fun enterSubmits() {
        assertEquals(EditEvent.SubmitLine, mapped(KeyboardEvent("Enter")))
    }

    @Test
    fun altEnterInsertsNewline() {
        assertEquals(EditEvent.InsertNewline, mapped(KeyboardEvent("Enter", alt = true)))
    }

    @Test
    fun editingKeysAreMapped() {
        assertEquals(EditEvent.Backspace, mapped(KeyboardEvent("Backspace")))
        assertEquals(EditEvent.DeleteForward, mapped(KeyboardEvent("Delete")))
        assertEquals(EditEvent.MoveLeft, mapped(KeyboardEvent("ArrowLeft")))
        assertEquals(EditEvent.MoveRight, mapped(KeyboardEvent("ArrowRight")))
        assertEquals(EditEvent.MoveHome, mapped(KeyboardEvent("Home")))
        assertEquals(EditEvent.MoveEnd, mapped(KeyboardEvent("End")))
    }

    @Test
    fun printableCharactersAreInserted() {
        assertEquals(EditEvent.InsertText("a"), mapped(KeyboardEvent("a")))
        assertEquals(EditEvent.InsertText(" "), mapped(KeyboardEvent(" ")))
        assertEquals(EditEvent.InsertText("ß"), mapped(KeyboardEvent("ß")))
    }

    @Test
    fun ctrlCInterrupts() {
        assertEquals(EditEvent.Interrupt, mapped(KeyboardEvent("c", ctrl = true)))
    }

    @Test
    fun unknownKeysAreSkipped() {
        assertNull(mapped(KeyboardEvent("F5")))
        assertNull(mapped(KeyboardEvent("c", ctrl = true, alt = true)))
        assertNull(mapped(KeyboardEvent("ArrowLeft", alt = true)))
    }
}