package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecCommandToolTest {

    private fun <T> runBlockingNoSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun resumeWith(res: Result<T>) {
                result = res
            }
        })
        return result!!.getOrThrow()
    }

    private fun execute(arguments: String): ToolResult.CommandResult {
        val tool = ExecCommandTool()
        val result = runBlockingNoSuspend {
            tool.execute(buildJsonObject { put("command", JsonPrimitive(arguments)) }, verbose = false)
        }
        return assertIs<ToolResult.CommandResult>(result)
    }

    @Test
    fun testExecuteEchoWithArgs() {
        val result = execute("echo hello world")
        assertEquals(0, result.exitStatus.code)
        assertEquals(null, result.exitStatus.signal)
        assertEquals("hello world\n", result.output)
    }

    @Test
    fun testExecuteMissingCommandReturnsError() {
        val tool = ExecCommandTool()
        val arguments = buildJsonObject { /* no command */ }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertIs<ToolResult.Error>(result)
        assertEquals("Missing required parameter: command", result.message)
    }

    @Test
    fun testExecuteCapturesExitCode() {
        val result = execute("sh -c 'exit 2'")
        assertEquals(2, result.exitStatus.code)
        assertEquals(null, result.exitStatus.signal)
    }

    @Test
    fun testExecuteAlwaysReportsExitCode() {
        val result = execute("echo hello world")
        assertTrue(result.toContentString().endsWith("[exit code: 0]"))
    }

    @Test
    fun testExecuteEmptyOutputUsesPlaceholder() {
        val result = execute("true")
        assertEquals("", result.output)
        assertEquals(
            "(no output)\n\n[exit code: 0]",
            result.toContentString()
        )
    }

    @Test
    fun testExecuteFailingCommandReportsExitCode() {
        val result = execute("ls /definitely-not-a-directory-xyz 2>&1")
        assertTrue(result.exitStatus.code != 0, "expected non-zero exit, got ${result.exitStatus}")
        assertTrue(result.toContentString().contains("[exit code: "))
    }

    @Test
    fun testExecuteSignalDeathReportsSignal() {
        val result = execute("kill -SEGV \$\$")
        assertEquals(null, result.exitStatus.code)
        assertEquals("SIGSEGV", result.exitStatus.signal)
    }

    @Test
    fun testExecuteCapturesStderrOfEveryCommandInAList() {
        val result = execute("echo out; sh -c 'echo err >&2'; echo done")
        assertTrue(result.output.contains("out"))
        assertTrue(result.output.contains("err"))
        assertTrue(result.output.contains("done"))
    }

    @Test
    fun testExecuteCapturesStderrOfPipelineProducer() {
        val result = execute("sh -c 'echo pipe-err >&2'; echo pipe | cat")
        assertTrue(result.output.contains("pipe-err"))
        assertTrue(result.output.contains("pipe"))
    }

    @Test
    fun testExecuteEmptyCommandReportsSuccess() {
        val result = execute("")
        assertEquals(0, result.exitStatus.code)
        assertEquals("", result.output)
    }

    @Test
    fun testExecuteTrailingCommentIsIgnored() {
        val result = execute("echo out # trailing comment")
        assertEquals(0, result.exitStatus.code)
        assertTrue(result.output.contains("out"))
    }

    @Test
    fun testExecuteTruncatesLargeOutputWithSpillFile() {
        val result = execute("yes '0123456789012345678901234567890123456789' | head -c 25000")
        val truncation = result.truncation
        assertTrue(truncation != null, "expected truncation for 25000-char output")
        assertTrue(truncation.head.length <= 10_000)
        assertTrue(truncation.tail.length <= 10_000)
        assertTrue(truncation.omittedCount > 0)
        assertTrue(truncation.spillPath.startsWith("/tmp/sara-exec-"))
        // full output recoverable via the spill file
        assertEquals(25_000, readSpillFile(truncation.spillPath).length)
        deleteSpillFile(truncation.spillPath)
    }
}
