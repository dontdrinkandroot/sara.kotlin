package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WebSearchToolTest {

    @Test
    fun testFunctionDescriptionSerialization() {
        val webSearchTool = WebSearchTool(SearxngClient("http://localhost:8080"))

        val tools = listOf(
            Tool(function = webSearchTool.getFunctionDescription())
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
        assertEquals("web_search", funcDesc["name"]?.jsonPrimitive?.content)
        assertEquals("Search the web and return the results", funcDesc["description"]?.jsonPrimitive?.content)

        val parameters = funcDesc["parameters"]?.jsonObject
        assertNotNull(parameters)
        assertEquals("object", parameters["type"]?.jsonPrimitive?.content)
        val properties = parameters["properties"]?.jsonObject
        assertNotNull(properties)
        val queryProp = properties["query"]?.jsonObject
        assertNotNull(queryProp)
        assertEquals("string", queryProp["type"]?.jsonPrimitive?.content)
        val required = parameters["required"]?.jsonArray
        assertNotNull(required)
        assertEquals(1, required.size)
        assertEquals("query", required[0].jsonPrimitive.content)
    }
}
