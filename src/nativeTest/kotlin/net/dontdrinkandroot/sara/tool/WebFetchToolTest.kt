package net.dontdrinkandroot.sara.tool

import ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebFetchToolTest {

    @Test
    fun testFunctionDescriptionSerialization() {
        val tool = WebFetchTool(WebFetchClient())

        val tools = listOf(
            Tool(function = tool.getFunctionDescription())
        )

        val request = ChatCompletionRequest(
            model = "unit-test-model",
            messages = listOf(Message(role = "user", content = "fetch a web page")),
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
        assertEquals("web_fetch", funcDesc["name"]?.jsonPrimitive?.content)
        assertEquals(
            "Fetch the content of a web page and return it as Markdown, text, or HTML",
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

        val formatProp = properties["format"]?.jsonObject
        assertNotNull(formatProp)
        assertEquals("string", formatProp["type"]?.jsonPrimitive?.content)
        val enumValues = formatProp["enum"]?.jsonArray
        assertNotNull(enumValues)
        assertEquals(3, enumValues.size)
        val enumStrings = enumValues.map { it.jsonPrimitive.content }
        assertTrue("markdown" in enumStrings)
        assertTrue("text" in enumStrings)
        assertTrue("html" in enumStrings)

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
        val tool = WebFetchTool(WebFetchClient())
        val arguments = kotlinx.serialization.json.buildJsonObject { /* no url */ }

        val result = runBlockingTest { tool.execute(arguments, verbose = false) }
        assertTrue(result is ToolResult.Error)
        assertEquals("Missing required parameter: url", (result as ToolResult.Error).message)
    }

    @Test
    fun testInvalidFormatReturnsError() {
        val tool = WebFetchTool(WebFetchClient())
        val arguments = kotlinx.serialization.json.buildJsonObject {
            put("url", kotlinx.serialization.json.JsonPrimitive("https://example.com"))
            put("format", kotlinx.serialization.json.JsonPrimitive("pdf"))
        }

        val result = runBlockingTest { tool.execute(arguments, verbose = false) }
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("Invalid format"))
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
