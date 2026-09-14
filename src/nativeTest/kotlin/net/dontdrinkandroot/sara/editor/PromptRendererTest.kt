package net.dontdrinkandroot.sara.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class PromptRendererTest {

    /** Records target calls as readable strings, tracking the simulated cursor row. */
    private class RecordingTarget : RenderTarget {
        val calls = mutableListOf<String>()
        var row = 0

        override fun moveUp(count: Int) {
            row -= count
            calls.add("up($count)")
        }

        override fun moveDown(count: Int) {
            row += count
            calls.add("down($count)")
        }

        override fun moveRight(count: Int) {
            calls.add("right($count)")
        }

        override fun moveToStartOfLine() {
            calls.add("startOfLine")
        }

        override fun moveToNextLine() {
            row += 1
            calls.add("nextLine")
        }

        override fun clearLine() {
            calls.add("clearLine")
        }

        override fun print(text: String) {
            calls.add("print('$text')")
        }
    }

    private fun renderAll(target: RecordingTarget, vararg buffers: LineBuffer) {
        val renderer = PromptRenderer(target)
        buffers.forEach { renderer.render(it) }
    }

    @Test
    fun rendersPromptPrefixAndBuffer() {
        val target = RecordingTarget()
        renderAll(target, LineBuffer("hello", 5))

        assertEquals(
            listOf("print('> hello')", "startOfLine", "right(7)"),
            target.calls,
        )
    }

    @Test
    fun emptyBufferRendersBarePrompt() {
        val target = RecordingTarget()
        renderAll(target, LineBuffer())

        assertEquals(listOf("print('> ')", "startOfLine", "right(2)"), target.calls)
    }

    @Test
    fun cursorInsideLineIsPositioned() {
        val target = RecordingTarget()
        renderAll(target, LineBuffer("hello", 2))

        assertEquals(listOf("print('> hello')", "startOfLine", "right(4)"), target.calls)
    }

    @Test
    fun multilineBufferRendersContinuationRows() {
        val target = RecordingTarget()
        renderAll(target, LineBuffer("one\ntwo", 7))

        // Cursor at end: row 1, col 3 -> prefix(2) + 3 = right(5).
        // The continuation row must be created with nextLine (CR+LF), not clamped down(1).
        assertEquals(
            listOf("print('> one')", "nextLine", "print('  two')", "startOfLine", "right(5)"),
            target.calls,
        )
    }

    @Test
    fun cursorOnSecondRowIsPositioned() {
        val target = RecordingTarget()
        renderAll(target, LineBuffer("one\ntwo", 5))

        // Cursor after "one\nt": row 1, col 1 -> prefix(2) + 1 = right(3)
        assertEquals(
            listOf("print('> one')", "nextLine", "print('  two')", "startOfLine", "right(3)"),
            target.calls,
        )
    }

    @Test
    fun cursorOnFirstRowOfMultilineMovesBackUp() {
        val target = RecordingTarget()
        renderAll(target, LineBuffer("one\ntwo", 2))

        // Cursor on row 0 col 2: after printing both rows the cursor must move up one row.
        assertEquals(
            listOf("print('> one')", "nextLine", "print('  two')", "up(1)", "startOfLine", "right(4)"),
            target.calls,
        )
    }

    @Test
    fun rerenderErasesPreviouslyRenderedRows() {
        val target = RecordingTarget()
        val renderer = PromptRenderer(target)
        renderer.render(LineBuffer("one\ntwo", 7))
        target.calls.clear()

        renderer.render(LineBuffer("x", 1))

        // Two rows were rendered; both must be erased before the new single row prints.
        assertEquals(
            listOf(
                "up(1)",
                "startOfLine",
                "clearLine",
                "down(1)",
                "startOfLine",
                "clearLine",
                "up(1)",
                "print('> x')",
                "startOfLine",
                "right(3)",
            ),
            target.calls,
        )
    }

    @Test
    fun finishMovesBelowTheBlock() {
        val target = RecordingTarget()
        val renderer = PromptRenderer(target)
        renderer.render(LineBuffer("one\ntwo", 7)) // cursor on row 1
        target.calls.clear()

        renderer.finish()

        // From the last row finishing only needs the final nextLine (scroll-safe even
        // when the block sits on the bottom screen rows).
        assertEquals(listOf("nextLine"), target.calls)
    }

    @Test
    fun finishAfterMultilineFromFirstRowMovesDown() {
        val target = RecordingTarget()
        val renderer = PromptRenderer(target)
        renderer.render(LineBuffer("one\ntwo", 2)) // cursor on row 0
        target.calls.clear()

        renderer.finish()

        // From row 0: down(1) reaches the last rendered row, then nextLine lands below it.
        assertEquals(listOf("down(1)", "nextLine"), target.calls)
    }

    @Test
    fun rowCreationNeverUsesClampedCursorDown() {
        // Regression pin: a three-row buffer must create both continuation rows via
        // nextLine (CR+LF scrolls; cursor-down clamps at the bottom screen margin).
        val target = RecordingTarget()
        renderAll(target, LineBuffer("a\nb\nc", 5))

        assertEquals(2, target.calls.count { it == "nextLine" })
        assertEquals(0, target.calls.count { it == "down(1)" })
    }
}