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

class ExaContentsToolTest {

    @Test
    fun testFunctionDescriptionSerialization() {
        val tool = ExaContentsTool(ExaClient("unit-test-key"))

        val tools = listOf(
            Tool(function = tool.getFunctionDescription())
        )

        val request = ChatCompletionRequest(
            model = "unit-test-model",
            messages = listOf(Message(role = "user", content = "extract content")),
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
        assertEquals("exa_contents", funcDesc["name"]?.jsonPrimitive?.content)
        assertEquals(
            "Extract clean, LLM-ready content from a web page via Exa",
            funcDesc["description"]?.jsonPrimitive?.content
        )

        val parameters = funcDesc["parameters"]?.jsonObject
        assertNotNull(parameters)
        assertEquals("object", parameters["type"]?.jsonPrimitive?.content)
        val properties = parameters["properties"]?.jsonObject
        assertNotNull(properties)

        val urlProp = properties["url"]?.jsonObject
        assertNotNull(urlProp)
        assertEquals("string", urlProp["type"]?.jsonPrimitive?.content)

        val maxLengthProp = properties["max_length"]?.jsonObject
        assertNotNull(maxLengthProp)
        assertEquals("integer", maxLengthProp["type"]?.jsonPrimitive?.content)

        val required = parameters["required"]?.jsonArray
        assertNotNull(required)
        assertEquals(1, required.size)
        assertEquals("url", required[0].jsonPrimitive.content)
    }

    @Test
    fun testMissingUrlReturnsError() {
        val tool = ExaContentsTool(ExaClient("unit-test-key"))
        val arguments = buildJsonObject { /* no url */ }

        val result = runBlockingTest { tool.execute(arguments, verbose = false) }
        assertTrue(result is ToolResult.Error)
        assertEquals("Missing required parameter: url", result.message)
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
