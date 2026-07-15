package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.JsonObject
import net.dontdrinkandroot.sara.FunctionDescription
import net.dontdrinkandroot.sara.SearxngClient
import net.dontdrinkandroot.sara.WebFetchClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolExecutorIsSafeTest {

    @Test
    fun defaultIsSafeIsFalse() {
        val tool = object : ToolExecutor {
            override val name: String = "default_tool"
            override val description: String = "test"
            override fun getFunctionDescription() = FunctionDescription(
                name = name,
                description = description,
                parameters = JsonObject(emptyMap())
            )

            override suspend fun execute(arguments: JsonObject, verbose: Boolean) =
                ToolResult.Success("ok")
        }
        assertFalse(tool.isSafe)
    }

    @Test
    fun readFileToolIsSafe() {
        assertTrue(ReadFileTool().isSafe)
    }

    @Test
    fun webFetchToolIsSafe() {
        assertTrue(WebFetchTool(WebFetchClient()).isSafe)
    }

    @Test
    fun webSearchToolIsSafe() {
        assertTrue(WebSearchTool(SearxngClient("http://localhost:8080")).isSafe)
    }

    @Test
    fun execCommandToolIsNotSafe() {
        assertFalse(ExecCommandTool().isSafe)
    }

    @Test
    fun writeFileToolIsNotSafe() {
        assertFalse(WriteFileTool().isSafe)
    }
}
