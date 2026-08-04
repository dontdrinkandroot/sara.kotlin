package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.*
import net.dontdrinkandroot.sara.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExaSearchToolTest {

    @Test
    fun testFunctionDescriptionSerialization() {
        val tool = ExaSearchTool(ExaClient("unit-test-key"))

        val tools = listOf(
            Tool(function = tool.getFunctionDescription())
        )

        val request = ChatCompletionRequest(
            model = "unit-test-model",
            messages = listOf(Message(role = "user", content = "search the web")),
            stream = false,
            tools = tools,
            toolChoice = ToolChoice.Auto
        )

        val json = Json.encodeToString(request)
        val root = Json.parseToJsonElement(json).jsonObject

        val toolsJson = root["tools"]?.jsonArray
        assertNotNull(toolsJson)
        assertEquals(1, toolsJson.size)
        val toolJson = toolsJson[0].jsonObject
        assertEquals("function", toolJson["type"]?.jsonPrimitive?.content)
        val funcDesc = toolJson["function"]?.jsonObject
        assertNotNull(funcDesc)
        assertEquals("exa_search", funcDesc["name"]?.jsonPrimitive?.content)
        assertEquals(
            "Search the web with Exa and return ranked results with highlights",
            funcDesc["description"]?.jsonPrimitive?.content
        )

        val parameters = funcDesc["parameters"]?.jsonObject
        assertNotNull(parameters)
        assertEquals("object", parameters["type"]?.jsonPrimitive?.content)
        val properties = parameters["properties"]?.jsonObject
        assertNotNull(properties)
        val queryProp = properties["query"]?.jsonObject
        assertNotNull(queryProp)
        assertEquals("string", queryProp["type"]?.jsonPrimitive?.content)
        val numResultsProp = properties["num_results"]?.jsonObject
        assertNotNull(numResultsProp)
        assertEquals("integer", numResultsProp["type"]?.jsonPrimitive?.content)
        val required = parameters["required"]?.jsonArray
        assertNotNull(required)
        assertEquals(1, required.size)
        assertEquals("query", required[0].jsonPrimitive.content)
    }

    @Test
    fun testMissingQueryReturnsError() {
        val tool = ExaSearchTool(ExaClient("unit-test-key"))
        val arguments = buildJsonObject { /* no query */ }

        val result = runBlockingTest { tool.execute(arguments, verbose = false) }
        assertTrue(result is ToolResult.Error)
        assertEquals("Missing required parameter: query", result.message)
    }

    private fun <T> runBlockingTest(block: suspend () -> T): T {
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
}
