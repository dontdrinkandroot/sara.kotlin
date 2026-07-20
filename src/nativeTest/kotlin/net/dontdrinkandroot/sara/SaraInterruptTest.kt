package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*
import net.dontdrinkandroot.sara.configuration.Configuration
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.tool.ToolExecutor
import net.dontdrinkandroot.sara.tool.ToolRegistry
import net.dontdrinkandroot.sara.tool.ToolResult
import kotlin.concurrent.AtomicReference
import kotlin.test.Test
import kotlin.test.assertTrue

class SaraInterruptTest {

    private val terminal = Terminal()
    private val logger = NoOpLogger

    private fun configuration() = Configuration(
        model = "test-model",
        apiKey = "test-key",
        baseUrl = "http://localhost",
        braveMode = false,
        systemPrompt = null,
    )

    private fun sara(
        interruptSource: FakeInterruptSource,
        llmClient: LlmClient,
        inputs: MutableList<String>,
        toolRegistry: ToolRegistry = ToolRegistry(),
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

    private fun assistantResponse(content: String): ChatCompletionResponse =
        ChatCompletionResponse(
            id = "test-id",
            choices = listOf(
                Choice(
                    finishReason = "stop",
                    message = Message(role = "assistant", content = content)
                )
            ),
            created = 0L,
            model = "test-model",
            `object` = "chat.completion",
        )

    private fun toolCallsResponse(toolCallIds: List<String>, toolName: String = "test_tool"): ChatCompletionResponse =
        ChatCompletionResponse(
            id = "test-id",
            choices = listOf(
                Choice(
                    finishReason = "tool_calls",
                    message = Message(
                        role = "assistant",
                        toolCalls = toolCallIds.map { id ->
                            ToolCall(id = id, function = FunctionCall(name = toolName, arguments = "{}"))
                        }
                    )
                )
            ),
            created = 0L,
            model = "test-model",
            `object` = "chat.completion",
        )

    private fun toolExecutor(name: String, isSafe: Boolean, execute: suspend () -> Unit): ToolExecutor =
        object : ToolExecutor {
            override val name = name
            override val description = "test tool"
            override val isSafe = isSafe
            override fun getFunctionDescription() = FunctionDescription(
                name = name,
                description = "test tool",
                parameters = kotlinx.serialization.json.JsonObject(emptyMap())
            )

            override suspend fun execute(
                arguments: kotlinx.serialization.json.JsonObject,
                verbose: Boolean
            ): ToolResult {
                execute()
                return ToolResult.Success("done")
            }
        }

    @Test
    fun interruptDuringLlmRequestCancelsTurnAndReturnsToPrompt() = runBlocking {
        val interruptSource = FakeInterruptSource()
        val llmCallStarted = CompletableDeferred<Unit>()
        var callCount = 0
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
                callCount++
                if (callCount == 1) {
                    llmCallStarted.complete(Unit)
                    delay(Long.MAX_VALUE)
                    error("should not reach here")
                }
                return assistantResponse("Hello back!")
            }

            override fun close() {}
        }

        val inputs = mutableListOf("hello", "", "")
        val sara = sara(interruptSource, llmClient, inputs)

        val saraJob = launch { sara.run() }
        llmCallStarted.await()
        interruptSource.trigger()

        withTimeout(5000) { saraJob.join() }
        assertTrue(callCount == 1, "LLM should only be called once (turn was interrupted)")
    }

    @Test
    fun normalFlowWithoutInterruptCompletesTurn() = runBlocking {
        val interruptSource = FakeInterruptSource()
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
            ): ChatCompletionResponse = assistantResponse("Hello!")

            override fun close() {}
        }

        val inputs = mutableListOf("hello", "")
        val sara = sara(interruptSource, llmClient, inputs)

        withTimeout(5000) {
            sara.run()
        }
    }

    @Test
    fun staleInterruptFlagDoesNotBleedIntoNextTurn() = runBlocking {
        val interruptSource = FakeInterruptSource()
        val llmCallStarted = CompletableDeferred<Unit>()
        var callCount = 0
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
                callCount++
                if (callCount == 1) {
                    llmCallStarted.complete(Unit)
                    delay(Long.MAX_VALUE)
                    error("should not reach here")
                }
                return assistantResponse("Hello back!")
            }

            override fun close() {}
        }

        val inputs = mutableListOf("hello", "what was the last message?", "")
        val sara = sara(interruptSource, llmClient, inputs)

        val saraJob = launch { sara.run() }
        llmCallStarted.await()
        interruptSource.trigger()

        withTimeout(5000) { saraJob.join() }
        assertTrue(callCount == 2, "LLM should be called twice: first interrupted, second completes. Got $callCount")
    }

    @Test
    fun interruptAtPermissionPromptReturnsInterrupted() {
        val interruptSource = FakeInterruptSource()
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
            ): ChatCompletionResponse = assistantResponse("ok")

            override fun close() {}
        }

        val inputs = mutableListOf("")
        val sara = sara(interruptSource, llmClient, inputs)

        interruptSource.trigger()
        val result = sara.askForToolPermission("test_tool", "{}")

        assertTrue(result is Sara.PermissionResult.Interrupted)
    }

    @Test
    fun interruptDuringToolExecutionAppendsSyntheticToolResults() = runBlocking {
        val interruptSource = FakeInterruptSource()
        val toolExecutionStarted = CompletableDeferred<Unit>()
        val capturedMessages = mutableListOf<Message>()
        var callCount = 0
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
                callCount++
                if (callCount == 1) {
                    return toolCallsResponse(listOf("call-1", "call-2"))
                }
                capturedMessages.addAll(messages)
                return assistantResponse("recovered")
            }

            override fun close() {}
        }

        val toolRegistry = ToolRegistry()
        toolRegistry.register(toolExecutor("test_tool", isSafe = true) {
            toolExecutionStarted.complete(Unit)
            delay(Long.MAX_VALUE)
        })

        val inputs = mutableListOf("run tools", "what happened?", "")
        val sara = sara(interruptSource, llmClient, inputs, toolRegistry)

        val saraJob = launch { sara.run() }
        toolExecutionStarted.await()
        interruptSource.trigger()

        withTimeout(5000) { saraJob.join() }

        assertTrue(callCount == 2, "LLM should be called again after the interrupted turn. Got $callCount")
        listOf("call-1", "call-2").forEach { id ->
            val toolMessage = capturedMessages.firstOrNull { it.role == "tool" && it.toolCallId == id }
            assertTrue(toolMessage != null, "Synthetic tool result missing for $id")
            assertTrue(
                toolMessage.content == "Error: Tool execution interrupted by user (Ctrl+C)",
                "Unexpected content for $id: ${toolMessage.content}"
            )
        }
    }

    @Test
    fun interruptAtToolPermissionPromptAppendsSyntheticToolResults() = runBlocking {
        val interruptSource = FakeInterruptSource()
        val capturedMessages = mutableListOf<Message>()
        var callCount = 0
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
                callCount++
                if (callCount == 1) {
                    return toolCallsResponse(listOf("call-1", "call-2"))
                }
                capturedMessages.addAll(messages)
                return assistantResponse("recovered")
            }

            override fun close() {}
        }

        val toolRegistry = ToolRegistry()
        toolRegistry.register(toolExecutor("test_tool", isSafe = false) {})

        // Second readLine() answers the permission prompt and triggers the interrupt,
        // simulating Ctrl+C while the prompt is waiting.
        val inputs = mutableListOf("run tools", "y", "what happened?", "")
        val sara = Sara(
            terminal = terminal,
            configuration = configuration(),
            logger = logger,
            llmClient = llmClient,
            toolRegistry = toolRegistry,
            systemPromptProvider = object : SystemPromptProvider {
                override fun provide() = ""
            },
            interruptSource = interruptSource,
            inputReader = Sara.InputReader {
                val line = inputs.removeAt(0)
                if (line == "y") interruptSource.trigger()
                line
            },
        )

        val saraJob = launch { sara.run() }
        withTimeout(5000) { saraJob.join() }

        assertTrue(callCount == 2, "LLM should be called again after the interrupted turn. Got $callCount")
        listOf("call-1", "call-2").forEach { id ->
            val toolMessage = capturedMessages.firstOrNull { it.role == "tool" && it.toolCallId == id }
            assertTrue(toolMessage != null, "Synthetic tool result missing for $id")
            assertTrue(
                toolMessage.content == "Error: Tool execution interrupted by user (Ctrl+C)",
                "Unexpected content for $id: ${toolMessage.content}"
            )
        }
    }
}

class FakeInterruptSource : InterruptSource {
    private object Marker

    private val flag = AtomicReference<Any?>(null)
    private val turnJob = AtomicReference<Job?>(null)

    fun trigger() {
        flag.value = Marker
        turnJob.value?.cancel()
    }

    override fun consumeInterrupt(): Boolean = flag.compareAndSet(Marker, null)

    override fun setTurnJob(job: Job?) {
        turnJob.value = job
    }
}
