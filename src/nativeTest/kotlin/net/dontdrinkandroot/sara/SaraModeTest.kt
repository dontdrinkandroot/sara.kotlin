package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.dontdrinkandroot.sara.configuration.Configuration
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.tool.ToolExecutor
import net.dontdrinkandroot.sara.tool.ToolRegistry
import net.dontdrinkandroot.sara.tool.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaraModeTest {

    private val terminal = Terminal()
    private val logger = NoOpLogger

    private fun configuration() = Configuration(
        model = "test-model",
        apiKey = "test-key",
        baseUrl = "http://localhost",
        braveMode = true,
        systemPrompt = null,
    )

    private fun captureToolsLlmClient(
        toolsCapture: MutableList<List<Tool>?> = mutableListOf(),
    ): LlmClient {
        return object : LlmClient {
            override suspend fun chatCompletion(
                model: String,
                messages: List<Message>,
                maxTokens: Int?,
                temperature: Double?,
                topP: Double?,
                frequencyPenalty: Double?,
                presencePenalty: Double?,
                tools: List<Tool>?,
                toolChoice: ToolChoice?
            ): ChatCompletionResponse {
                toolsCapture.add(tools)
                return ChatCompletionResponse(
                    id = "test-id",
                    choices = listOf(
                        Choice(
                            finishReason = "stop",
                            message = Message(role = "assistant", content = "Acknowledged.")
                        )
                    ),
                    created = 0L,
                    model = "test-model",
                    `object` = "chat.completion",
                )
            }

            override fun close() {}
        }
    }

    private fun sara(
        inputs: MutableList<String>,
        toolRegistry: ToolRegistry = ToolRegistry(),
        llmClient: LlmClient = captureToolsLlmClient(),
        interruptSource: FakeInterruptSource = FakeInterruptSource(),
    ): Sara = Sara(
        terminal = terminal,
        configuration = configuration(),
        logger = logger,
        llmClient = llmClient,
        toolRegistry = toolRegistry,
        systemPromptProvider = object : SystemPromptProvider {
            override fun provide() = ""
        },
        interruptSource = interruptSource,
        inputReader = Sara.InputReader { inputs.removeAt(0) },
    )

    @Test
    fun defaultModeIsExec() = runBlocking {
        val toolsCapture = mutableListOf<List<Tool>?>()
        val llmClient = captureToolsLlmClient(toolsCapture)

        val toolRegistry = ToolRegistry()
        toolRegistry.register(FakePlanTool("write_file", availableInPlanMode = false))
        toolRegistry.register(FakePlanTool("exec_command", availableInPlanMode = true))

        val inputs = mutableListOf("hello", "")
        val sara = sara(inputs, toolRegistry, llmClient)
        sara.run()

        assertEquals(1, toolsCapture.size, "LLM should be called once")
        val toolsSent = toolsCapture[0]!!
        val toolNames = toolsSent.map { it.function.name }
        assertTrue("write_file" in toolNames, "write_file should be available in default exec mode")
        assertTrue("exec_command" in toolNames, "exec_command should be available in default exec mode")
    }

    @Test
    fun switchToPlanModeInjectsSystemMessage() = runBlocking {
        val capturedMessages = mutableListOf<List<Message>>()
        val llmClient = object : LlmClient {
            override suspend fun chatCompletion(
                model: String,
                messages: List<Message>,
                maxTokens: Int?,
                temperature: Double?,
                topP: Double?,
                frequencyPenalty: Double?,
                presencePenalty: Double?,
                tools: List<Tool>?,
                toolChoice: ToolChoice?
            ): ChatCompletionResponse {
                capturedMessages.add(messages.toList())
                return ChatCompletionResponse(
                    id = "test-id",
                    choices = listOf(
                        Choice(
                            finishReason = "stop",
                            message = Message(role = "assistant", content = "Ok")
                        )
                    ),
                    created = 0L,
                    model = "test-model",
                    `object` = "chat.completion",
                )
            }

            override fun close() {}
        }

        val inputs = mutableListOf("/plan", "hello", "")
        val sara = sara(inputs, llmClient = llmClient)
        sara.run()

        // The LLM should have been called once (after "hello")
        assertEquals(1, capturedMessages.size, "LLM should be called once")
        val msgs = capturedMessages[0]

        // First message is the system prompt, second should be the plan mode instruction
        assertTrue(msgs.size >= 3, "Should have system prompt, plan mode instruction, and user message")
        val planInstruction = msgs[1]
        assertEquals("system", planInstruction.role)
        assertTrue(
            planInstruction.content!!.contains("PLAN MODE"),
            "Should contain plan mode instruction"
        )
        assertEquals("user", msgs[2].role)
        assertEquals("hello", msgs[2].content)
    }

    @Test
    fun switchToExecModeInjectsSystemMessage() = runBlocking {
        val capturedMessages = mutableListOf<List<Message>>()
        val llmClient = object : LlmClient {
            override suspend fun chatCompletion(
                model: String,
                messages: List<Message>,
                maxTokens: Int?,
                temperature: Double?,
                topP: Double?,
                frequencyPenalty: Double?,
                presencePenalty: Double?,
                tools: List<Tool>?,
                toolChoice: ToolChoice?
            ): ChatCompletionResponse {
                capturedMessages.add(messages.toList())
                return ChatCompletionResponse(
                    id = "test-id",
                    choices = listOf(
                        Choice(
                            finishReason = "stop",
                            message = Message(role = "assistant", content = "Ok")
                        )
                    ),
                    created = 0L,
                    model = "test-model",
                    `object` = "chat.completion",
                )
            }

            override fun close() {}
        }

        // Start in plan, switch to exec, then send a message
        val inputs = mutableListOf("/plan", "/exec", "hello", "")
        val sara = sara(inputs, llmClient = llmClient)
        sara.run()

        assertEquals(1, capturedMessages.size)
        val msgs = capturedMessages[0]

        // system prompt, plan instruction, exec instruction, user message
        assertTrue(msgs.size >= 4, "Should have system prompt, plan instruction, exec instruction, and user message")
        val execInstruction = msgs[2]
        assertEquals("system", execInstruction.role)
        assertTrue(
            execInstruction.content!!.contains("EXECUTION MODE"),
            "Should contain exec mode instruction"
        )
        assertEquals("user", msgs[3].role)
        assertEquals("hello", msgs[3].content)
    }

    @Test
    fun sameModeCommandDoesNotInjectDuplicateMessage() = runBlocking {
        val capturedMessages = mutableListOf<List<Message>>()
        val llmClient = object : LlmClient {
            override suspend fun chatCompletion(
                model: String,
                messages: List<Message>,
                maxTokens: Int?,
                temperature: Double?,
                topP: Double?,
                frequencyPenalty: Double?,
                presencePenalty: Double?,
                tools: List<Tool>?,
                toolChoice: ToolChoice?
            ): ChatCompletionResponse {
                capturedMessages.add(messages.toList())
                return ChatCompletionResponse(
                    id = "test-id",
                    choices = listOf(
                        Choice(
                            finishReason = "stop",
                            message = Message(role = "assistant", content = "Ok")
                        )
                    ),
                    created = 0L,
                    model = "test-model",
                    `object` = "chat.completion",
                )
            }

            override fun close() {}
        }

        // /exec when already in exec mode should not inject a message
        val inputs = mutableListOf("/exec", "hello", "")
        val sara = sara(inputs, llmClient = llmClient)
        sara.run()

        assertEquals(1, capturedMessages.size)
        val msgs = capturedMessages[0]
        // system prompt + user message only (no mode instruction)
        assertEquals(2, msgs.size, "Should not inject duplicate mode message")
        assertEquals("system", msgs[0].role)
        assertEquals("user", msgs[1].role)
        assertEquals("hello", msgs[1].content)
    }

    @Test
    fun planModeExcludesWriteFileTool() = runBlocking {
        val toolsCapture = mutableListOf<List<Tool>?>()
        val llmClient = captureToolsLlmClient(toolsCapture)

        val toolRegistry = ToolRegistry()
        toolRegistry.register(FakePlanTool("write_file", availableInPlanMode = false))
        toolRegistry.register(FakePlanTool("exec_command", availableInPlanMode = true))
        toolRegistry.register(FakePlanTool("read_file", availableInPlanMode = true))

        // Switch to plan, then send a message
        val inputs = mutableListOf("/plan", "analyze this", "")
        val sara = sara(inputs, toolRegistry, llmClient)
        sara.run()

        assertEquals(1, toolsCapture.size, "LLM should be called once")
        val toolsSent = toolsCapture[0]!!
        val toolNames = toolsSent.map { it.function.name }

        assertTrue("exec_command" in toolNames, "exec_command should be available in plan mode")
        assertTrue("read_file" in toolNames, "read_file should be available in plan mode")
        assertFalse("write_file" in toolNames, "write_file should NOT be available in plan mode")
    }

    @Test
    fun execModeIncludesAllTools() = runBlocking {
        val toolsCapture = mutableListOf<List<Tool>?>()
        val llmClient = captureToolsLlmClient(toolsCapture)

        val toolRegistry = ToolRegistry()
        toolRegistry.register(FakePlanTool("write_file", availableInPlanMode = false))
        toolRegistry.register(FakePlanTool("exec_command", availableInPlanMode = true))
        toolRegistry.register(FakePlanTool("read_file", availableInPlanMode = true))

        // Stay in exec mode, send a message
        val inputs = mutableListOf("do something", "")
        val sara = sara(inputs, toolRegistry, llmClient)
        sara.run()

        assertEquals(1, toolsCapture.size)
        val toolsSent = toolsCapture[0]!!
        val toolNames = toolsSent.map { it.function.name }

        assertTrue("exec_command" in toolNames, "exec_command should be available in exec mode")
        assertTrue("read_file" in toolNames, "read_file should be available in exec mode")
        assertTrue("write_file" in toolNames, "write_file should be available in exec mode")
    }

    @Test
    fun toolRegistryFilterWithPlanMode() {
        val registry = ToolRegistry()
        registry.register(FakePlanTool("write_file", availableInPlanMode = false))
        registry.register(FakePlanTool("exec_command", availableInPlanMode = true))
        registry.register(FakePlanTool("read_file", availableInPlanMode = true))

        val allTools = registry.getToolSchemas()
        assertEquals(3, allTools.size, "All tools should be returned without filter")

        val planTools = registry.getToolSchemas { it.availableInPlanMode }
        assertEquals(2, planTools.size, "Only plan-safe tools should be returned")
        val planNames = planTools.map { it.function.name }
        assertTrue("exec_command" in planNames)
        assertTrue("read_file" in planNames)
        assertFalse("write_file" in planNames)
    }

    private class FakePlanTool(
        override val name: String,
        override val availableInPlanMode: Boolean = true,
    ) : ToolExecutor {
        override val description: String = "fake tool for testing"
        override fun getFunctionDescription() = FunctionDescription(
            name = name,
            description = description,
            parameters = JsonObject(emptyMap())
        )

        override suspend fun execute(arguments: JsonObject, verbose: Boolean) = ToolResult.Success("ok")
    }
}
