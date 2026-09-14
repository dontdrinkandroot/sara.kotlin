package net.dontdrinkandroot.sara.editor

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.RawModeScope
import kotlin.time.Duration

/**
 * Source of raw-mode input events for one prompt read. Opening may fail (non-interactive
 * terminal), which the caller treats as end of input.
 */
interface RawEventReader : AutoCloseable {
    /** Returns the next event, or null on EOF/closed input. */
    fun readEvent(): InputEvent?
}

/** [RawEventReader] backed by an open Mordant [RawModeScope]. */
class RawModeScopeReader(private val scope: RawModeScope) : RawEventReader {

    override fun readEvent(): InputEvent? = scope.readEventOrNull(Duration.INFINITE)

    /** Restores the terminal to cooked mode. */
    override fun close() = scope.close()
}