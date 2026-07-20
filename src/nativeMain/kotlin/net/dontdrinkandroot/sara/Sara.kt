package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.progress.progressBarLayout
import com.github.ajalt.mordant.widgets.progress.spinner
import com.github.ajalt.mordant.widgets.progress.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dontdrinkandroot.sara.configuration.Configuration
import net.dontdrinkandroot.sara.logger.Logger
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.tool.ToolExecutor
import net.dontdrinkandroot.sara.tool.ToolRegistry
import net.dontdrinkandroot.sara.tool.ToolResult
import kotlin.coroutines.coroutineContext

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
    private val interruptSource: InterruptSource = SignalInterruptSource,
    private val inputReader: InputReader = InputReader { readlnOrNull() },
) {

    private var currentMode: Mode = Mode.EXEC

    /**
     * Reads a single line of user input from the configured source (stdin in production,
     * a scripted source in tests). Returns null on EOF.
     */
    fun interface InputReader {
        fun readLine(): String?
    }

    internal sealed interface PermissionResult {
        data object Allowed : PermissionResult
        data class Denied(val reason: String?) : PermissionResult
        data object Interrupted : PermissionResult
    }

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

            when (userInput.trim()) {
                "/plan" -> {
                    if (currentMode != Mode.PLAN) {
                        currentMode = Mode.PLAN
                        terminal.println("[sara] Switched to plan mode.")
                        println()
                        messages.add(Message(role = "system", content = Mode.PLAN.instruction))
                    }
                    continue
                }

                "/exec" -> {
                    if (currentMode != Mode.EXEC) {
                        currentMode = Mode.EXEC
                        terminal.println("[sara] Switched to execution mode.")
                        println()
                        messages.add(Message(role = "system", content = Mode.EXEC.instruction))
                    }
                    continue
                }
            }

            messages.add(Message(role = "user", content = userInput))
            println()

            runTurnWithInterrupt(messages)
        }
    }

    private fun promptUserInput(): String? {
        terminal.println(cyan("User [${currentMode.label}]:"))
        terminal.print("> ")
        val input = inputReader.readLine()

        if (input.isNullOrBlank()) {
            terminal.println()
            terminal.println("Goodbye!")
            return null
        }

        return input
    }

    /**
     * Runs a single conversation turn, allowing the user to interrupt it with Ctrl+C.
     *
     * Before the turn starts any stale interrupt flag is consumed (e.g. one raised at the
     * prompt). The turn runs in a child [Job] that the [InterruptSource] can cancel when an
     * interrupt arrives. On cancellation a system message is appended so the LLM reconciles
     * the partial state before the next user message.
     */
    private suspend fun runTurnWithInterrupt(messages: MutableList<Message>) {
        // Clear any stale flag from the prompt phase (e.g. Ctrl+C pressed at the prompt).
        interruptSource.consumeInterrupt()

        val turnCancelled = coroutineScope {
            val turnJob = launch { processConversationTurn(messages) }
            interruptSource.setTurnJob(turnJob)
            try {
                turnJob.join()
            } finally {
                interruptSource.setTurnJob(null)
            }
            turnJob.isCancelled
        }

        // Consume the flag that was set by the signal handler during this turn so it cannot
        // bleed into the next turn or cause a force-exit at the next prompt.
        interruptSource.consumeInterrupt()

        if (turnCancelled) {
            terminal.println(red("\n^C Interrupted."))
            messages.add(
                Message(
                    role = "system",
                    content = "The user interrupted this turn. Stop and wait for the next user message."
                )
            )
        }
    }

    private suspend fun processConversationTurn(messages: MutableList<Message>) {
        var awaitingModelResponse = true

        while (awaitingModelResponse) {
            coroutineContext.ensureActive()
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
            text { "Processing" }
        }.animateInCoroutine(terminal, total = 1)

        return coroutineScope {
            val progressJob = launch { progress.execute() }
            try {
                llmClient.chatCompletion(
                    configuration.model,
                    messages = messages,
                    tools = toolRegistry.getToolSchemas {
                        it.availableInPlanMode || currentMode != Mode.PLAN
                    },
                )
            } finally {
                withContext(NonCancellable) {
                    progress.update { completed = 1 }
                    progressJob.join()
                    progress.clear()
                }
            }
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

    private suspend fun processToolCalls(message: Message, messages: MutableList<Message>) {
        val toolCalls = message.toolCalls!!
        logger.debug("Model requested ${toolCalls.size} tool call(s)")
        messages.add(message)

        val answeredToolCallIds = mutableSetOf<String>()
        try {
            for (toolCall in toolCalls) {
                coroutineContext.ensureActive()
                executeToolCall(toolCall, messages)
                answeredToolCallIds.add(toolCall.id)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                toolCalls
                    .filter { it.id !in answeredToolCallIds }
                    .forEach { toolCall ->
                        addToolErrorMessage(
                            toolCall,
                            toolCall.function.name,
                            "Error: Tool execution interrupted by user (Ctrl+C)",
                            messages
                        )
                    }
            }
            throw e
        }
    }

    private suspend fun executeToolCall(toolCall: ToolCall, messages: MutableList<Message>) {
        val toolName = toolCall.function.name
        val toolArgs = toolCall.function.arguments

        logger.debug("Tool call: $toolName with args: $toolArgs")

        val tool = toolRegistry.get(toolName)
        if (tool == null) {
            addToolErrorMessage(toolCall, toolName, "Error: Tool '$toolName' not found", messages)
            logger.error("Tool not found: $toolName")
            return
        }

        val permission = checkToolPermission(tool, toolArgs)
        when (permission) {
            PermissionResult.Interrupted ->
                throw CancellationException("Interrupted by user (Ctrl+C) at permission prompt")

            is PermissionResult.Denied -> {
                val denialMessage = buildDenialMessage(permission.reason)
                addToolErrorMessage(toolCall, toolName, denialMessage, messages)
                logger.warn("Tool execution denied by user" + (permission.reason?.takeIf { it.isNotBlank() }
                    ?.let { ": $it" } ?: ""))
                return
            }

            PermissionResult.Allowed -> { /* continue to execute */
            }
        }

        val result = executeToolWithErrorHandling(tool, toolArgs)
        addToolResultMessage(toolCall, toolName, result, messages)
        logToolResult(result)
    }

    internal fun checkToolPermission(tool: ToolExecutor, toolArgs: String): PermissionResult {
        if (configuration.braveMode) {
            logger.debug("Brave mode enabled: executing tool '${tool.name}' without confirmation")
            announceToolExecution(tool, toolArgs)
            return PermissionResult.Allowed
        }

        if (tool.isSafe) {
            logger.debug("Tool '${tool.name}' is safe: executing without confirmation")
            announceToolExecution(tool, toolArgs)
            return PermissionResult.Allowed
        }

        return askForToolPermission(tool.name, toolArgs)
    }

    private fun announceToolExecution(tool: ToolExecutor, toolArgs: String) {
        terminal.println(yellow("[sara] ${formatToolAnnouncement(tool.name, toolArgs)}"))
    }

    internal fun buildDenialMessage(reason: String?): String {
        val trimmed = reason?.trim()
        return if (!trimmed.isNullOrEmpty()) {
            "Error: Tool execution denied by user. Reason: $trimmed"
        } else {
            "Error: Tool execution denied by user"
        }
    }

    private suspend fun executeToolWithErrorHandling(tool: ToolExecutor, toolArgs: String): ToolResult {
        return try {
            val argsJson = kotlinx.serialization.json.Json.parseToJsonElement(toolArgs)
                    as? kotlinx.serialization.json.JsonObject
                ?: kotlinx.serialization.json.JsonObject(emptyMap())

            tool.execute(argsJson, configuration.verbose)
        } catch (e: CancellationException) {
            throw e
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

    internal fun askForToolPermission(toolName: String, toolArgs: String): PermissionResult {
        terminal.println("[sara] The assistant wants to use tool '$toolName' with arguments: $toolArgs")
        terminal.print("[sara] Allow execution? [y/N]: ")
        val response = inputReader.readLine()

        if (interruptSource.consumeInterrupt()) {
            return PermissionResult.Interrupted
        }

        terminal.println()

        if (response?.trim()?.lowercase() == "y" || response?.trim()?.lowercase() == "yes") {
            return PermissionResult.Allowed
        }

        terminal.print("[sara] Optional reason for declining (press Enter to omit): ")
        val reason = inputReader.readLine()

        if (interruptSource.consumeInterrupt()) {
            return PermissionResult.Interrupted
        }

        terminal.println()
        return PermissionResult.Denied(reason)
    }
}

/**
 * Formats a compact one-line announcement for a tool that is executed without a
 * confirmation prompt (brave mode or safe tools). Arguments are truncated to
 * [maxLength] characters to keep the terminal output readable.
 */
internal fun formatToolAnnouncement(toolName: String, toolArgs: String, maxLength: Int = 120): String {
    val trimmedArgs = toolArgs.trim()
    if (trimmedArgs.isEmpty()) {
        return "Executing tool '$toolName'"
    }
    val displayArgs = if (trimmedArgs.length > maxLength) {
        "${trimmedArgs.take(maxLength)}..."
    } else {
        trimmedArgs
    }
    return "Executing tool '$toolName': $displayArgs"
}
