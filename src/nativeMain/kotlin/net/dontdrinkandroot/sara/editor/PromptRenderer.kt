package net.dontdrinkandroot.sara.editor

/**
 * Renders the prompt buffer as a block of rows (one per logical line) with proper cursor
 * positioning, redrawing every row on each change so multiline buffers (continuation
 * lines, pastes) do not leave stale text behind.
 *
 * Stateful across renders within one input read: remembers the previously rendered row
 * count and cursor row to erase them. Create a fresh instance per submission.
 *
 * Row transitions towards *new* rows use [RenderTarget.moveToNextLine] (CR+LF) because
 * cursor-down does not scroll: at the bottom screen margin it would clamp and overwrite
 * the current row instead of creating the continuation row. Moves *within* the already
 * rendered block use plain cursor movements, whose clamping is harmless there.
 */
class PromptRenderer(private val target: RenderTarget) : LineRenderer {

    private var renderedRows = 0
    private var cursorRow = 0

    override fun render(buffer: LineBuffer) {
        val lines = if (buffer.chars.isEmpty()) listOf("") else buffer.chars.split('\n')
        val rows = lines.size
        val newCursorRow = buffer.chars.substring(0, buffer.cursor).count { it == '\n' }
        val lineStart = if (buffer.cursor == 0) 0 else buffer.chars.lastIndexOf('\n', buffer.cursor - 1) + 1
        val cursorCol = buffer.cursor - lineStart

        eraseRenderedRows()

        lines.forEachIndexed { index, line ->
            if (index > 0) target.moveToNextLine()
            target.print(rowPrefix(index) + line)
        }

        // The cursor now sits at the end of the last row; reposition it to the logical
        // cursor position within the block.
        val up = rows - 1 - newCursorRow
        if (up > 0) target.moveUp(up)
        target.moveToStartOfLine()
        target.moveRight(rowPrefix(newCursorRow).length + cursorCol)

        renderedRows = rows
        cursorRow = newCursorRow
    }

    override fun finish() {
        if (renderedRows == 0) return
        val down = renderedRows - 1 - cursorRow
        if (down > 0) target.moveDown(down)
        target.moveToNextLine()
        renderedRows = 0
        cursorRow = 0
    }

    private fun eraseRenderedRows() {
        if (renderedRows == 0) return
        if (cursorRow > 0) target.moveUp(cursorRow)
        target.moveToStartOfLine()
        target.clearLine()
        repeat(renderedRows - 1) {
            target.moveDown(1)
            target.moveToStartOfLine()
            target.clearLine()
        }
        if (renderedRows > 1) target.moveUp(renderedRows - 1)
    }

    private fun rowPrefix(row: Int): String = if (row == 0) PROMPT_PREFIX else CONTINUATION_PREFIX

    companion object {
        /** Both prefixes have the same width so cursor columns stay uniform. */
        const val PROMPT_PREFIX = "> "
        const val CONTINUATION_PREFIX = "  "
    }
}