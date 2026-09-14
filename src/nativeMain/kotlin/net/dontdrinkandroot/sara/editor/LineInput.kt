package net.dontdrinkandroot.sara.editor

/** The outcome of reading one user submission from a [LineInput]. */
sealed interface LineReadResult {
    /** The user submitted [text]; it may span multiple lines. */
    data class Submitted(val text: String) : LineReadResult

    /** The user abandoned the current input (Ctrl+C). */
    data object Aborted : LineReadResult

    /** End of input (Ctrl+D or EOF on stdin). */
    data object Eof : LineReadResult
}

/**
 * Reads one user submission for the REPL prompt.
 *
 * Implemented by [MordantLineInput] (interactive raw mode) and [ScriptedLineInput] (tests).
 */
fun interface LineInput {
    fun readSubmission(): LineReadResult
}