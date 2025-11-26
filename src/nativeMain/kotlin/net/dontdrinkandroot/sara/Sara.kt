package net.dontdrinkandroot.sara

import ToolExecutor
import ToolResult
import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.progress.progressBarLayout
import com.github.ajalt.mordant.widgets.progress.spinner
import com.github.ajalt.mordant.widgets.progress.text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.dontdrinkandroot.sara.configuration.Configuration
import net.dontdrinkandroot.sara.logger.Logger
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.tool.ToolRegistry

/**
 * Sara application core: handles the interactive REPL and tool-calling loop.
 *
 * Main is responsible only for wiring dependencies and invoking [run].
 */
class Sara(
    private val terminal: Terminal,
    private val configuration: Configuration,
    private val logger: Logger,
    private val llmClient: LlmClient,
    private val toolRegistry: ToolRegistry,
    private val systemPromptProvider: SystemPromptProvider,
) {

    suspend fun run() {
        val systemPrompt = systemPromptProvider.provide()
        logger.debug("System prompt: $systemPrompt")
        val messages = mutableListOf(Message(role = "system", content = systemPrompt))

        logger.debug("REPL started. Submit empty line or EOF (Ctrl+D) to exit.")

        runConversationLoop(messages)
    }

    private suspend fun runConversationLoop(messages: MutableList<Message>) {
        while (true) {
            val userInput = promptUserInput() ?: break
            messages.add(Message(role = "user", content = userInput))
            println()

            processConversationTurn(messages)
        }
    }

    private fun promptUserInput(): String? {
        terminal.println(cyan("User:"))
        terminal.print("> ")
        val input = readlnOrNull()

        if (input.isNullOrBlank()) {
            terminal.println()
            terminal.println("Goodbye!")
            return null
        }

        return input
    }

    private suspend fun processConversationTurn(messages: MutableList<Message>) {
        var awaitingModelResponse = true

        while (awaitingModelResponse) {
            val response = fetchLlmResponse(messages)
            val message = extractMessageFromResponse(response) ?: run {
                awaitingModelResponse = false
                return
            }

            awaitingModelResponse = when {
                message.hasToolCalls() -> {
                    processToolCalls(message, messages)
                    true
                }

                else -> {
                    displayAssistantMessage(message, messages)
                    false
                }
            }
        }
    }

    private suspend fun fetchLlmResponse(messages: List<Message>): ChatCompletionResponse {
        val progress = progressBarLayout {
            spinner(Spinner.Dots())
            text { "Processing${if (configuration.webSearchEnabled) " (with search)" else ""}" }
        }.animateInCoroutine(terminal, total = 1)

        val response = coroutineScope {
            val progressJob = launch { progress.execute() }
            try {
                llmClient.chatCompletion(
                    configuration.model,
                    messages = messages,
                    enableWebSearch = configuration.webSearchEnabled,
                    tools = toolRegistry.getToolSchemas(),
                ).also {
                    progress.update { completed = 1 }
                }
            } finally {
                progressJob.join()
            }
        }

        clearProgressLine()
        return response
    }

    private fun clearProgressLine() {
        terminal.cursor.move {
            up(1)
            clearLine()
        }
    }

    private fun extractMessageFromResponse(response: ChatCompletionResponse): Message? {
        val choice = response.choices.firstOrNull()
        if (choice == null) {
            logger.error("No response from model.")
            return null
        }

        val message = choice.message
        if (message == null) {
            logger.error("No message in response.")
            return null
        }

        return message
    }

    private fun Message.hasToolCalls(): Boolean =
        toolCalls != null && toolCalls.isNotEmpty()

    private fun processToolCalls(message: Message, messages: MutableList<Message>) {
        logger.debug("Model requested ${message.toolCalls!!.size} tool call(s)")
        messages.add(message)

        for (toolCall in message.toolCalls) {
            executeToolCall(toolCall, messages)
        }
    }

    private fun executeToolCall(toolCall: ToolCall, messages: MutableList<Message>) {
        val toolName = toolCall.function.name
        val toolArgs = toolCall.function.arguments

        logger.debug("Tool call: $toolName with args: $toolArgs")

        val tool = toolRegistry.get(toolName)
        if (tool == null) {
            addToolErrorMessage(toolCall, toolName, "Error: Tool '$toolName' not found", messages)
            logger.error("Tool not found: $toolName")
            return
        }

        if (!isToolExecutionAllowed(toolName, toolArgs)) {
            addToolErrorMessage(toolCall, toolName, "Error: Tool execution denied by user", messages)
            logger.warn("Tool execution denied by user")
            return
        }

        val result = executeToolWithErrorHandling(tool, toolArgs)
        addToolResultMessage(toolCall, toolName, result, messages)
        logToolResult(result)
    }

    private fun isToolExecutionAllowed(toolName: String, toolArgs: String): Boolean {
        if (configuration.braveMode) {
            logger.debug("Brave mode enabled: executing tool '$toolName' without confirmation")
            return true
        }

        return askForToolPermission(toolName, toolArgs)
    }

    private fun executeToolWithErrorHandling(tool: ToolExecutor, toolArgs: String): ToolResult {
        return try {
            val argsJson = kotlinx.serialization.json.Json.parseToJsonElement(toolArgs)
                    as? kotlinx.serialization.json.JsonObject
                ?: kotlinx.serialization.json.JsonObject(emptyMap())

            tool.execute(argsJson, configuration.verbose)
        } catch (e: Exception) {
            logger.error("Tool execution error: ${e.message}")
            ToolResult.Error("Failed to execute tool: ${e.message}")
        }
    }

    private fun addToolErrorMessage(
        toolCall: ToolCall,
        toolName: String,
        errorMessage: String,
        messages: MutableList<Message>
    ) {
        messages.add(
            Message(
                role = "tool",
                toolCallId = toolCall.id,
                name = toolName,
                content = errorMessage
            )
        )
    }

    private fun addToolResultMessage(
        toolCall: ToolCall,
        toolName: String,
        result: ToolResult,
        messages: MutableList<Message>
    ) {
        messages.add(
            Message(
                role = "tool",
                toolCallId = toolCall.id,
                name = toolName,
                content = result.toContentString()
            )
        )
    }

    private fun logToolResult(result: ToolResult) {
        val resultContent = result.toContentString()
        val truncatedResult = if (resultContent.length > 100) {
            "${resultContent.take(100)}..."
        } else {
            resultContent
        }
        logger.debug("Tool result: $truncatedResult")
    }

    private fun displayAssistantMessage(message: Message, messages: MutableList<Message>) {
        val content = message.content
        if (content != null) {
            terminal.println(green("Sara:"))
            terminal.println(Markdown(content))
            println()
            messages.add(message)
        } else {
            logger.error("No content returned by the model.")
        }
    }

    private fun askForToolPermission(toolName: String, toolArgs: String): Boolean {
        terminal.println("[sara] The assistant wants to use tool '$toolName' with arguments: $toolArgs")
        terminal.print("[sara] Allow execution? [y/N]: ")
        val response = readlnOrNull()?.trim()?.lowercase()
        terminal.println()
        return response == "y" || response == "yes"
    }
}
