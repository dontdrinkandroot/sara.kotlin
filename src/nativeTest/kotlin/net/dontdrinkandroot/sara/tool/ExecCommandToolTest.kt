package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun testExecuteEchoWithArgs() {
        val tool = ExecCommandTool()
        val arguments = buildJsonObject {
            put("command", JsonPrimitive("echo hello world"))
        }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        when (result) {
            is ToolResult.Success -> {
                // echo appends a newline; trim for comparison
                assertEquals("hello world", result.output.trim())
            }

            else -> kotlin.test.fail("Expected Success, got $result")
        }
    }

    @Test
    fun testExecuteMissingCommandReturnsError() {
        val tool = ExecCommandTool()
        val arguments = buildJsonObject { /* no command */ }

        val result = runBlockingNoSuspend { tool.execute(arguments, verbose = false) }
        assertTrue(result is ToolResult.Error)
        val err = result as ToolResult.Error
        assertEquals("Missing required parameter: command", err.message)
    }
}
