package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.serialization.json.JsonObject
import net.dontdrinkandroot.sara.configuration.Configuration
import net.dontdrinkandroot.sara.logger.Logger
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.tool.ToolExecutor
import net.dontdrinkandroot.sara.tool.ToolRegistry
import net.dontdrinkandroot.sara.tool.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaraToolPermissionTest {

    private val terminal = Terminal()
    private val logger = NoOpLogger

    private fun sara(braveMode: Boolean, inputReader: Sara.InputReader): Sara {
        val configuration = Configuration(
            model = "test-model",
            apiKey = "test-key",
            baseUrl = "http://localhost",
            braveMode = braveMode,
            systemPrompt = null,
        )
        return Sara(
            terminal = terminal,
            configuration = configuration,
            logger = logger,
            llmClient = DefaultLlmClient("http://localhost", "test-key"),
            toolRegistry = ToolRegistry(),
            systemPromptProvider = object : SystemPromptProvider {
                override fun provide() = ""
            },
            inputReader = inputReader,
        )
    }

    private fun safeTool(name: String = "safe_tool"): ToolExecutor = FakeTool(name, isSafe = true)
    private fun unsafeTool(name: String = "unsafe_tool"): ToolExecutor = FakeTool(name, isSafe = false)

    @Test
    fun braveModeAllowsAnyToolWithoutPrompt() {
        val sara = sara(braveMode = true, inputReader = Sara.InputReader { error("Should not prompt") })
        val result = sara.checkToolPermission(unsafeTool(), "{}")
        assertTrue(result is Sara.PermissionResult.Allowed)
    }

    @Test
    fun safeToolAllowedWithoutPromptWhenBraveModeOff() {
        val sara = sara(braveMode = false, inputReader = Sara.InputReader { error("Should not prompt") })
        val result = sara.checkToolPermission(safeTool(), "{}")
        assertTrue(result is Sara.PermissionResult.Allowed)
    }

    @Test
    fun unsafeToolPromptedWhenBraveModeOff_userAccepts() {
        val sara = sara(braveMode = false, inputReader = Sara.InputReader { "y" })
        val result = sara.checkToolPermission(unsafeTool(), "{}")
        assertTrue(result is Sara.PermissionResult.Allowed)
    }

    @Test
    fun unsafeToolPromptedWhenBraveModeOff_userDeclinesWithReason() {
        val inputs = mutableListOf("n", "dangerous")
        val sara = sara(braveMode = false, inputReader = Sara.InputReader { inputs.removeAt(0) })
        val result = sara.checkToolPermission(unsafeTool(), "{}")
        assertTrue(result is Sara.PermissionResult.Denied)
        assertEquals("dangerous", result.reason)
    }

    @Test
    fun unsafeToolPromptedWhenBraveModeOff_userDeclinesWithoutReason() {
        val inputs = mutableListOf("n", "")
        val sara = sara(braveMode = false, inputReader = Sara.InputReader { inputs.removeAt(0) })
        val result = sara.checkToolPermission(unsafeTool(), "{}")
        assertTrue(result is Sara.PermissionResult.Denied)
        assertEquals("", result.reason)
    }

    @Test
    fun buildDenialMessageWithReason() {
        val sara = sara(braveMode = false, inputReader = Sara.InputReader { null })
        assertEquals(
            "Error: Tool execution denied by user. Reason: too risky",
            sara.buildDenialMessage("too risky")
        )
    }

    @Test
    fun buildDenialMessageWithNullReason() {
        val sara = sara(braveMode = false, inputReader = Sara.InputReader { null })
        assertEquals(
            "Error: Tool execution denied by user",
            sara.buildDenialMessage(null)
        )
    }

    @Test
    fun buildDenialMessageWithBlankReason() {
        val sara = sara(braveMode = false, inputReader = Sara.InputReader { null })
        assertEquals(
            "Error: Tool execution denied by user",
            sara.buildDenialMessage("   ")
        )
    }

    private object NoOpLogger : Logger {
        override fun debug(message: String) {}
        override fun info(message: String) {}
        override fun warn(message: String) {}
        override fun error(message: String) {}
    }

    private class FakeTool(
        override val name: String,
        override val isSafe: Boolean,
    ) : ToolExecutor {
        override val description: String = "fake"
        override fun getFunctionDescription() = FunctionDescription(
            name = name,
            description = description,
            parameters = JsonObject(emptyMap())
        )

        override suspend fun execute(arguments: JsonObject, verbose: Boolean) =
            ToolResult.Success("ok")
    }
}
