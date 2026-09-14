@file:OptIn(ExperimentalForeignApi::class)

package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import net.dontdrinkandroot.sara.configuration.Configuration
import net.dontdrinkandroot.sara.session.SessionLoad
import net.dontdrinkandroot.sara.session.SessionStore
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.tool.ToolRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaraSessionTest {

    private val terminal = Terminal()
    private val logger = NoOpLogger

    private fun configuration() = Configuration(
        model = "test-model",
        apiKey = "test-key",
        baseUrl = "http://localhost",
        braveMode = true,
        systemPrompt = null,
    )

    private fun okLlmClient(): LlmClient = object : LlmClient {
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
        ): ChatCompletionResponse = ChatCompletionResponse(
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

        override fun close() {}
    }

    /** Wraps another client and records the messages of every request. */
    private class RecordingLlmClient(private val delegate: LlmClient) : LlmClient {
        val recordedMessages = mutableListOf<List<Message>>()

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
            recordedMessages.add(messages.toList())
            return delegate.chatCompletion(
                model, messages, maxTokens, temperature, topP, frequencyPenalty, presencePenalty, tools, toolChoice
            )
        }

        override fun close() = delegate.close()
    }

    private fun sara(
        inputs: MutableList<String>,
        sessionStore: SessionStore,
        llmClient: LlmClient,
    ): Sara = Sara(
        terminal = terminal,
        configuration = configuration(),
        logger = logger,
        llmClient = llmClient,
        toolRegistry = ToolRegistry(),
        systemPromptProvider = object : SystemPromptProvider {
            override fun provide() = "You are SARA."
        },
        inputReader = Sara.InputReader { inputs.removeAt(0) },
        sessionStore = sessionStore,
    )

    @Test
    fun testContinueRestoresMessagesAndMode() = runBlocking {
        val store = FakeSessionStore()
        store.save(
            listOf(
                Message(role = "system", content = "You are SARA."),
                Message(role = "user", content = "Earlier question"),
                Message(role = "assistant", content = "Earlier answer"),
            ),
            Mode.PLAN,
        )

        val inputs = mutableListOf("y", "new question", "")
        val llm = RecordingLlmClient(okLlmClient())
        sara(inputs, store, llm).run()

        // Restored verbatim: saved system message kept, history in order, then the new turn
        val lastRequest = llm.recordedMessages.last()
        assertEquals(4, lastRequest.size)
        assertEquals("You are SARA.", lastRequest[0].content)
        assertEquals("Earlier question", lastRequest[1].content)
        assertEquals("Earlier answer", lastRequest[2].content)
        assertEquals("new question", lastRequest[3].content)

        // Re-persisted after the new exchange, mode restored too
        val persisted = store.savedSession
        assertTrue(persisted != null)
        assertEquals("PLAN", persisted.mode)
        assertEquals("new question", persisted.messages.last { it.role == "user" }.content)
    }

    @Test
    fun testDeclineDeletesSavedSessionAndStartsFresh() = runBlocking {
        val store = FakeSessionStore()
        store.save(
            listOf(
                Message(role = "system", content = "You are SARA."),
                Message(role = "user", content = "Old"),
            ),
            Mode.EXEC,
        )

        // Default answer (Enter) declines; the file must be deleted before the new session
        val inputs = mutableListOf("", "fresh question", "")
        val llm = RecordingLlmClient(okLlmClient())
        sara(inputs, store, llm).run()

        assertEquals(1, store.deleteCount)
        val sentMessages = llm.recordedMessages.first()
        assertEquals(listOf("You are SARA.", "fresh question"), sentMessages.map { it.content })

        val persisted = store.savedSession
        assertTrue(persisted != null)
        assertEquals("EXEC", persisted.mode)
        assertFalse(persisted.messages.any { it.content == "Old" })
    }

    @Test
    fun testFirstUserMessagePersistsSession() = runBlocking {
        val store = FakeSessionStore()
        assertEquals(SessionLoad.NoSession, store.load())

        val inputs = mutableListOf("hello", "")
        sara(inputs, store, okLlmClient()).run()

        val persisted = store.savedSession
        assertTrue(persisted != null)
        assertEquals("EXEC", persisted.mode)
        assertEquals(3, persisted.messages.size)
        assertEquals("system", persisted.messages[0].role)
        assertEquals("hello", persisted.messages[1].content)
        assertEquals("assistant", persisted.messages[2].role)
    }

    @Test
    fun testModeSwitchPersistsSession() = runBlocking {
        val store = FakeSessionStore()

        val inputs = mutableListOf("/plan", "")
        sara(inputs, store, okLlmClient()).run()

        val persisted = store.savedSession
        assertTrue(persisted != null)
        assertEquals("PLAN", persisted.mode)
        assertEquals(2, persisted.messages.size) // system + mode-switch instruction
    }

    @Test
    fun testSaveFailureDoesNotBreakTheSession() = runBlocking {
        val store = FakeSessionStore()
        store.failOnSave = true

        val inputs = mutableListOf("hello", "")
        val llm = RecordingLlmClient(okLlmClient())
        sara(inputs, store, llm).run()

        // The exchange still completed despite the persistence failure
        assertEquals(1, llm.recordedMessages.size)
    }

    @Test
    fun testCorruptSessionWarnsAndStartsFresh() = runBlocking {
        val store = FakeSessionStore()
        store.corruptOnLoad = true

        val inputs = mutableListOf("hello", "")
        val llm = RecordingLlmClient(okLlmClient())
        sara(inputs, store, llm).run()

        // No restore prompt is consumed, no delete (the .bak was already written by the store)
        val sentMessages = llm.recordedMessages.first()
        assertEquals(listOf("You are SARA.", "hello"), sentMessages.map { it.content })
        assertEquals(0, store.deleteCount)
    }
}
