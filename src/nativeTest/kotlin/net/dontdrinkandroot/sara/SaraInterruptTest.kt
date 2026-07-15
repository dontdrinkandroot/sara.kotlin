package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*
import net.dontdrinkandroot.sara.configuration.Configuration
import net.dontdrinkandroot.sara.logger.Logger
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.tool.ToolRegistry
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

    private object NoOpLogger : Logger {
        override fun debug(message: String) {}
        override fun info(message: String) {}
        override fun warn(message: String) {}
        override fun error(message: String) {}
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
