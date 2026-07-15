package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.JsonObject
import net.dontdrinkandroot.sara.FunctionDescription

/**
 * Represents a tool that can be called by the LLM assistant.
 */
interface ToolExecutor {
    val name: String
    val description: String

    /**
     * Whether this tool is safe to execute without explicit user confirmation.
     *
     * Safe tools (read-only, side-effect-free) bypass the permission prompt even when brave mode
     * is disabled. Unsafe tools always prompt unless brave mode is on. Defaults to false.
     */
    val isSafe: Boolean
        get() = false

    /**
     * Returns the function schema for this tool to be sent to the LLM.
     */
    fun getFunctionDescription(): FunctionDescription

    /**
     * Executes the tool with the given arguments.
     * @param arguments Parsed JSON arguments from the LLM
     * @param verbose Whether to enable verbose logging
     * @return The result of the tool execution
     */
    suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult
}
