package net.dontdrinkandroot.sara.editor

import com.github.ajalt.mordant.terminal.Terminal

/**
 * [RenderTarget] backed by a Mordant [Terminal]'s cursor and print API. Row transitions
 * to new rows use CR+LF (via rawPrint) rather than cursor-down: cursor-down clamps at
 * the bottom screen margin instead of scrolling, which would overwrite the last row
 * when the prompt sits at the bottom of the terminal window.
 */
class MordantRenderTarget(private val terminal: Terminal) : RenderTarget {

    override fun moveUp(count: Int) {
        terminal.cursor.move { up(count) }
    }

    override fun moveDown(count: Int) {
        terminal.cursor.move { down(count) }
    }

    override fun moveRight(count: Int) {
        if (count > 0) terminal.cursor.move { right(count) }
    }

    override fun moveToStartOfLine() {
        terminal.cursor.move { startOfLine() }
    }

    override fun moveToNextLine() {
        // CR + LF: the LF scrolls and creates the next row even at the bottom margin;
        // the CR returns to column 0. Harmless double-CR if OPOST translates \n itself.
        terminal.rawPrint("\r\n")
    }

    override fun clearLine() {
        terminal.cursor.move { clearLine() }
    }

    override fun print(text: String) {
        terminal.rawPrint(text)
    }
}