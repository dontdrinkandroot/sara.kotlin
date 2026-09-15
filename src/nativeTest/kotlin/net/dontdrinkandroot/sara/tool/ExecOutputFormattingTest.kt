package net.dontdrinkandroot.sara.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Specifications for the pure (non-executing) parts of exec_command: exit status decoding,
 * head+tail truncation, and the LLM-facing result format.
 */
class ExecOutputFormattingTest {

    // --- exit status decoding ---------------------------------------------------

    @Test
    fun decodesExitCodeZero() {
        val status = 0 // WIFEXITED, WEXITSTATUS = 0
        val decoded = decodeWaitStatus(status)
        assertEquals(ExitStatus(code = 0, signal = null), decoded)
    }

    @Test
    fun decodesNonZeroExitCode() {
        val status = 2 shl 8 // WIFEXITED, WEXITSTATUS = 2
        val decoded = decodeWaitStatus(status)
        assertEquals(ExitStatus(code = 2, signal = null), decoded)
    }

    @Test
    fun decodesSignalDeath() {
        val signal = 11 // SIGSEGV
        val status = signal // WIFSIGNALED, WTERMSIG = 11
        val decoded = decodeWaitStatus(status)
        assertEquals(ExitStatus(code = null, signal = "SIGSEGV"), decoded)
    }

    // --- status line ------------------------------------------------------------

    @Test
    fun statusLineForExitCode() {
        assertEquals("[exit code: 0]", statusLine(ExitStatus(code = 0, signal = null)))
        assertEquals("[exit code: 127]", statusLine(ExitStatus(code = 127, signal = null)))
    }

    @Test
    fun statusLineForSignal() {
        assertEquals("[killed by signal: SIGSEGV]", statusLine(ExitStatus(code = null, signal = "SIGSEGV")))
    }

    // --- truncation -------------------------------------------------------------

    @Test
    fun noTruncationAtExactCap() {
        val output = "a".repeat(20_000)
        val result = truncateHeadTail(output, inlineLimit = 20_000, halfLimit = 10_000)
        assertEquals(null, result)
    }

    @Test
    fun truncatesOneCharOverCap() {
        val output = "a".repeat(20_001)
        val result = truncateHeadTail(output, inlineLimit = 20_000, halfLimit = 10_000)!!
        assertEquals(10_000, result.head.length)
        assertEquals(10_000, result.tail.length)
        assertEquals(1, result.omittedCount)
        assertTrue(result.spillPath.startsWith("/tmp/sara-exec-"))
        assertTrue(result.spillPath.endsWith(".log"))
        deleteSpillFile(result.spillPath)
    }

    @Test
    fun headAndTailComeFromOutputEdges() {
        val output = (1..25_000).joinToString("") { (it % 10).toString() }
        val result = truncateHeadTail(output, inlineLimit = 20_000, halfLimit = 10_000)!!
        assertEquals(output.take(10_000), result.head)
        assertEquals(output.takeLast(10_000), result.tail)
        assertEquals(5_000, result.omittedCount)
        deleteSpillFile(result.spillPath)
    }

    @Test
    fun truncationSnapsToLineBoundaries() {
        val line = "x".repeat(59) + "\n" // 60 chars per line
        val output = line.repeat(400) // 24_000 chars
        val result = truncateHeadTail(output, inlineLimit = 20_000, halfLimit = 10_000)!!

        // head must not end mid-line: ends at a newline (or is empty)
        assertTrue(result.head.isEmpty() || result.head.endsWith("\n"))
        // tail must not start mid-line: preceded by a newline in the original output
        val tailStart = output.length - result.tail.length
        assertTrue(tailStart == 0 || output[tailStart - 1] == '\n')

        // omitted = what is neither head nor tail
        val omitted = output.length - result.head.length - result.tail.length
        assertEquals(result.omittedCount, omitted)
        // line snapping only ever shrinks the inline output, never grows it
        assertTrue(result.head.length <= 10_000 && result.tail.length <= 10_000)
        deleteSpillFile(result.spillPath)
    }

    @Test
    fun truncationNeverGrowsOmittedBeyondCapExcess() {
        // With line-aligned snapping the omitted region may exceed the raw omitted count,
        // but head+tail must never exceed the inline limit.
        val line = "y".repeat(59) + "\n"
        val output = line.repeat(335) // 20_100 chars
        val result = truncateHeadTail(output, inlineLimit = 20_000, halfLimit = 10_000)!!
        assertTrue(
            result.head.length + result.tail.length <= 20_000,
            "head+tail ${result.head.length + result.tail.length} exceeds inline limit"
        )
        assertTrue(result.omittedCount > 0)
        deleteSpillFile(result.spillPath)
    }

    // --- spill file -------------------------------------------------------------

    @Test
    fun spillFileContainsFullOutput() {
        val output = (1..25_000).joinToString("") { (it % 10).toString() }
        val result = truncateHeadTail(output, inlineLimit = 20_000, halfLimit = 10_000)!!
        val full = readSpillFile(result.spillPath)
        assertEquals(output, full)
        deleteSpillFile(result.spillPath)
    }

    @Test
    fun noSpillFileWhenNotTruncated() {
        val before = listSaraExecSpillFiles()
        val output = "b".repeat(19_999)
        truncateHeadTail(output, inlineLimit = 20_000, halfLimit = 10_000)
        assertEquals(before, listSaraExecSpillFiles())
    }

    // --- full result rendering ---------------------------------------------------

    @Test
    fun rendersCompleteResult() {
        val rendered = renderExecResult(
            output = "hello world\n",
            exitStatus = ExitStatus(code = 0, signal = null),
            truncation = null
        )
        assertEquals("hello world\n\n[exit code: 0]", rendered)
    }

    @Test
    fun rendersNoOutputPlaceholder() {
        val rendered = renderExecResult(
            output = "",
            exitStatus = ExitStatus(code = 0, signal = null),
            truncation = null
        )
        assertEquals("(no output)\n\n[exit code: 0]", rendered)
    }

    @Test
    fun rendersFailureWithOutput() {
        val rendered = renderExecResult(
            output = "ls: /nope: No such file or directory\n",
            exitStatus = ExitStatus(code = 2, signal = null),
            truncation = null
        )
        assertEquals(
            "ls: /nope: No such file or directory\n\n[exit code: 2]",
            rendered
        )
    }

    @Test
    fun rendersTruncationLineSeparately() {
        val truncation = TruncationInfo(
            head = "<head>",
            tail = "<tail>",
            omittedCount = 42,
            spillPath = "/tmp/sara-exec-abc123.log"
        )
        val rendered = renderExecResult(
            output = "<head><omitted><tail>",
            exitStatus = ExitStatus(code = 0, signal = null),
            truncation = truncation
        )
        assertEquals(
            "<head>\n" +
                "...[42 characters omitted — full log: /tmp/sara-exec-abc123.log; use read_file to inspect]...\n" +
                "<tail>\n" +
                "\n" +
                "[exit code: 0]\n" +
                "[output truncated: 42 characters omitted, full log: /tmp/sara-exec-abc123.log]",
            rendered
        )
    }

    @Test
    fun rendersTruncationWithEmptyTailWithoutExtraBlankLine() {
        // output ending exactly at a line boundary yields an empty tail excerpt
        val truncation = TruncationInfo(
            head = "<head>\n",
            tail = "",
            omittedCount = 42,
            spillPath = "/tmp/sara-exec-abc123.log"
        )
        val rendered = renderExecResult(
            output = "<head>\n<omitted>",
            exitStatus = ExitStatus(code = 0, signal = null),
            truncation = truncation
        )
        assertEquals(
            "<head>\n" +
                "...[42 characters omitted — full log: /tmp/sara-exec-abc123.log; use read_file to inspect]...\n" +
                "\n" +
                "[exit code: 0]\n" +
                "[output truncated: 42 characters omitted, full log: /tmp/sara-exec-abc123.log]",
            rendered
        )
    }
}
