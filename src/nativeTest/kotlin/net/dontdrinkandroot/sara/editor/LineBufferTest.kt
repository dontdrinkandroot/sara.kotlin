package net.dontdrinkandroot.sara.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class LineBufferTest {

    @Test
    fun emptyByDefault() {
        val buffer = LineBuffer()
        assertEquals("", buffer.chars)
        assertEquals(0, buffer.cursor)
    }

    @Test
    fun insertAtStartAndMiddle() {
        val buffer = LineBuffer("world", 0).insert("hello ")
        assertEquals("hello world", buffer.chars)
        assertEquals(6, buffer.cursor)

        val middle = LineBuffer("hello world", 6).insert("big ")
        assertEquals("hello big world", middle.chars)
        assertEquals(10, middle.cursor)
    }

    @Test
    fun insertAtEnd() {
        val buffer = LineBuffer("hello", 5).insert("!")
        assertEquals("hello!", buffer.chars)
        assertEquals(6, buffer.cursor)
    }

    @Test
    fun insertMultilineText() {
        val buffer = LineBuffer().insert("line1\nline2")
        assertEquals("line1\nline2", buffer.chars)
        assertEquals(11, buffer.cursor)
    }

    @Test
    fun deleteBackward() {
        assertEquals("hell", LineBuffer("hello", 5).deleteBackward().chars)
        assertEquals(4, LineBuffer("hello", 5).deleteBackward().cursor)
        // At the start: no-op returning the same instance
        val buffer = LineBuffer("hello", 0)
        assertSame(buffer, buffer.deleteBackward())
    }

    @Test
    fun deleteForward() {
        assertEquals("ello", LineBuffer("hello", 0).deleteForward().chars)
        assertEquals(0, LineBuffer("hello", 0).deleteForward().cursor)
        // At the end: no-op returning the same instance
        val buffer = LineBuffer("hello", 5)
        assertSame(buffer, buffer.deleteForward())
    }

    @Test
    fun moveLeftAndRightClampAtBounds() {
        val buffer = LineBuffer("ab", 2)
        assertEquals(1, buffer.moveLeft().cursor)
        assertEquals(0, buffer.moveLeft().moveLeft().cursor)
        assertEquals(0, buffer.moveLeft().moveLeft().moveLeft().cursor)
        assertEquals(1, buffer.moveLeft().moveLeft().moveRight().cursor)
    }

    @Test
    fun moveHomeAndEnd() {
        val buffer = LineBuffer("ab", 1)
        assertEquals(0, buffer.moveToStart().cursor)
        assertEquals(2, buffer.moveToEnd().cursor)
    }

    @Test
    fun cursorOutsideBufferIsRejected() {
        assertFailsWith<IllegalArgumentException> { LineBuffer("ab", 3) }
        assertFailsWith<IllegalArgumentException> { LineBuffer("ab", -1) }
    }
}