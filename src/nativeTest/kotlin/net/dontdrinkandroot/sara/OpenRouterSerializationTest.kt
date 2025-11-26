package net.dontdrinkandroot.sara

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.tool.ExecCommandTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenRouterSerializationTest {

    @Test
    fun testChatCompletionSerializationWithToolCalls() {
        val execCommandTool = ExecCommandTool()

        val tools = listOf(
            Tool(
                function = execCommandTool.getFunctionDescription()
            )
        )

        // Build messages including an assistant tool_call
        val userMsg = Message(role = "user", content = "List files")
        val toolCall = ToolCall(
            id = "call_abc123",
            function = FunctionCall(
                name = "exec_command",
                arguments = "{\"command\":\"ls -la\"}"
            )
        )
        val assistantMsg = Message(
            role = "assistant",
            toolCalls = listOf(toolCall)
        )

        val request = ChatCompletionRequest(
            model = "unit-test-model",
            messages = listOf(userMsg, assistantMsg),
            stream = false,
            tools = tools,
            toolChoice = ToolChoice.Auto
        )

        val json = Json.encodeToString(request)

        // Parse back to verify structure instead of brittle string compare
        val root = Json.parseToJsonElement(json).jsonObject

        // Verify model and stream present
        assertEquals("unit-test-model", root["model"]?.jsonPrimitive?.content)
        assertEquals(false, root["stream"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())

        // Verify tools serialization
        val toolsJson = root["tools"]?.jsonArray
        assertNotNull(toolsJson)
        assertEquals(1, toolsJson.size)
        val toolJson = toolsJson[0].jsonObject
        assertEquals("function", toolJson["type"]?.jsonPrimitive?.content)
        val funcDesc = toolJson["function"]?.jsonObject
        assertNotNull(funcDesc)
        assertEquals("exec_command", funcDesc["name"]?.jsonPrimitive?.content)
        assertEquals(
            "Execute a system command (including its arguments) and return its output",
            funcDesc["description"]?.jsonPrimitive?.content
        )
        assertNotNull(funcDesc["parameters"])

        // Verify messages and tool_calls
        val messagesJson = root["messages"]?.jsonArray
        assertNotNull(messagesJson)
        assertEquals(2, messagesJson.size)
        assertEquals("user", messagesJson[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("assistant", messagesJson[1].jsonObject["role"]?.jsonPrimitive?.content)

        val assistantObj = messagesJson[1].jsonObject
        val toolCallsJson = assistantObj["tool_calls"]?.jsonArray
        assertNotNull(toolCallsJson, "tool_calls should be serialized on assistant message")
        assertEquals(1, toolCallsJson.size)

        val toolCallObj = toolCallsJson[0].jsonObject
        assertEquals("call_abc123", toolCallObj["id"]?.jsonPrimitive?.content)
        assertEquals("function", toolCallObj["type"]?.jsonPrimitive?.content)

        val functionObj = toolCallObj["function"]?.jsonObject
        assertNotNull(functionObj)
        assertEquals("exec_command", functionObj["name"]?.jsonPrimitive?.content)
        // Arguments are a JSON string; ensure exact value
        assertEquals("{\"command\":\"ls -la\"}", functionObj["arguments"]?.jsonPrimitive?.content)

        // Verify tool_choice auto is serialized properly
        assertTrue(root.containsKey("tool_choice"))
        assertEquals("auto", root["tool_choice"]?.jsonPrimitive?.content)
    }
}
