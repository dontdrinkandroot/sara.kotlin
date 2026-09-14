package net.dontdrinkandroot.sara.editor

/**
 * Test/replay [LineInput]: each [nextLine] invocation yields one submission.
 *
 * `null` and the empty string mean end of input; any other string is submitted verbatim
 * (embedded newlines are preserved, matching a multiline paste).
 */
class ScriptedLineInput(private val nextLine: () -> String?) : LineInput {

    override fun readSubmission(): LineReadResult = when (val line = nextLine()) {
        null -> LineReadResult.Eof
        else -> if (line.isEmpty()) LineReadResult.Eof else LineReadResult.Submitted(line)
    }
}