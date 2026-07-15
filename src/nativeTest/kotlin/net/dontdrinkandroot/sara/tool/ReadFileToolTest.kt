package net.dontdrinkandroot.sara.tool

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadFileToolTest {

    private var tempCounter = 0

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

    @OptIn(ExperimentalForeignApi::class)
    private fun writeTempFile(content: String): String {
        val path = "/tmp/sara-readfile-test-${tempCounter++}.txt"
        val file = fopen(path, "w") ?: error("Could not create temp file")
        try {
            fputs(content, file)
        } finally {
            fclose(file)
        }
        return path
    }

    private fun successOutput(result: ToolResult): String {
        assertTrue(result is ToolResult.Success, "Expected Success, got $result")
        return (result as ToolResult.Success).output
    }

    private fun errorMessage(result: ToolResult): String {
        assertTrue(result is ToolResult.Error, "Expected Error, got $result")
        return (result as ToolResult.Error).message
    }

    @Test
    fun testReadFullFileNoTruncationMarker() {
        val content = "line1\nline2\nline3\n"
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals(content, successOutput(result))
    }

    @Test
    fun testReadWithOffset() {
        val content = "0123456789"
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("offset", JsonPrimitive(5))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals("56789", successOutput(result))
    }

    @Test
    fun testReadWithOffsetAndLimit() {
        val content = "0123456789" // 10 chars
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("offset", JsonPrimitive(5))
            put("limit", JsonPrimitive(5))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals("56789", successOutput(result))
    }

    @Test
    fun testTruncationMarkerContainsNextOffsetAndTotal() {
        val content = "0123456789ABCDEFGHIJ" // 20 chars
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("offset", JsonPrimitive(0))
            put("limit", JsonPrimitive(5))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        val output = successOutput(result)
        assertEquals("01234...[truncated, continue with offset=5, file has 20 characters]", output)
    }

    @Test
    fun testTruncationMarkerWithNonZeroOffset() {
        val content = "0123456789ABCDEFGHIJ" // 20 chars
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("offset", JsonPrimitive(5))
            put("limit", JsonPrimitive(10))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        val output = successOutput(result)
        assertEquals(
            "56789ABCDE...[truncated, continue with offset=15, file has 20 characters]",
            output
        )
    }

    @Test
    fun testOffsetAtEndOfFileReturnsEmptyWithFooter() {
        val content = "0123456789" // 10 chars
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("offset", JsonPrimitive(10))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals("[offset 10 is at or beyond end of file (10 characters)]", successOutput(result))
    }

    @Test
    fun testOffsetBeyondFileReturnsFooterWithTotal() {
        val content = "0123456789" // 10 chars
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("offset", JsonPrimitive(100))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals(
            "[offset 100 is at or beyond end of file (10 characters)]",
            successOutput(result)
        )
    }

    @Test
    fun testNoTrailingNewlineFullRead() {
        val content = "no newline here"
        val path = writeTempFile(content)
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals(content, successOutput(result))
    }

    @Test
    fun testEmptyFileOffsetAtEnd() {
        val path = writeTempFile("")
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("offset", JsonPrimitive(0))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals("[offset 0 is at or beyond end of file (0 characters)]", successOutput(result))
    }

    @Test
    fun testMissingPathReturnsError() {
        val tool = ReadFileTool()
        val arguments = buildJsonObject { /* no path */ }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertEquals("Missing required parameter: path", errorMessage(result))
    }

    @Test
    fun testNegativeOffsetReturnsError() {
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive("/tmp/does-not-matter"))
            put("offset", JsonPrimitive(-1))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        val msg = errorMessage(result)
        assertTrue(msg.contains("offset"), "Expected error to mention 'offset': $msg")
    }

    @Test
    fun testNonIntegerOffsetReturnsError() {
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive("/tmp/does-not-matter"))
            put("offset", JsonPrimitive("abc"))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        val msg = errorMessage(result)
        assertTrue(msg.contains("offset"), "Expected error to mention 'offset': $msg")
    }

    @Test
    fun testZeroLimitReturnsError() {
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive("/tmp/does-not-matter"))
            put("limit", JsonPrimitive(0))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        val msg = errorMessage(result)
        assertTrue(msg.contains("limit"), "Expected error to mention 'limit': $msg")
    }

    @Test
    fun testNonIntegerLimitReturnsError() {
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive("/tmp/does-not-matter"))
            put("limit", JsonPrimitive("abc"))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        val msg = errorMessage(result)
        assertTrue(msg.contains("limit"), "Expected error to mention 'limit': $msg")
    }

    @Test
    fun testNonExistentFileReturnsError() {
        val tool = ReadFileTool()
        val arguments = buildJsonObject {
            put("path", JsonPrimitive("/tmp/sara-does-not-exist-${tempCounter++}"))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        val msg = errorMessage(result)
        assertTrue(msg.contains("Failed to read file"), "Expected failure message: $msg")
    }

    @Test
    fun testDefaultLimitIs10000() {
        assertEquals(10000, ReadFileTool.DEFAULT_LIMIT)
    }
}
