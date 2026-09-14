package net.dontdrinkandroot.sara.editor

/**
 * The low-level terminal operations [PromptRenderer] needs, abstracted so the render
 * logic is testable without Mordant.
 */
interface RenderTarget {
    fun moveUp(count: Int)
    fun moveDown(count: Int)
    fun moveRight(count: Int)
    fun moveToStartOfLine()

    /**
     * Moves the cursor to column 0 of the NEXT row, creating that row by scrolling when
     * the cursor is on the last screen row (LF semantics, unlike clamped cursor-down).
     */
    fun moveToNextLine()

    fun clearLine()
    fun print(text: String)
}

/** The renderer contract consumed by [MordantLineInput]. */
interface LineRenderer {
    /** Redraws the buffer (which may span multiple rows) and positions the cursor. */
    fun render(buffer: LineBuffer)

    /** Moves the cursor below the rendered block so following output starts cleanly. */
    fun finish()
}