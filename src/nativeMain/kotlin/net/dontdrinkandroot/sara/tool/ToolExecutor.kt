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
     * Whether this tool can be executed without explicit user confirmation.
     *
     * A tool is "safe" when it does not alter the system state or the user configuration —
     * i.e. it only reads or has side effects confined to SARA's own bookkeeping (e.g.
     * `add_customization` only writes to SARA's own config file). Safe tools bypass the
     * permission prompt even when brave mode is disabled. Tools that mutate system state
     * or write to user-controlled paths (e.g. `write_file`, `exec_command`) override this
     * to false so they always prompt unless brave mode is on. Defaults to false.
     */
    val isSafe: Boolean
        get() = false

    /**
     * Whether this tool is available in PLAN MODE.
     *
     * Tools that can modify the system (e.g. `write_file`) should override this to false
     * so they are excluded from the tool list sent to the LLM in plan mode. Defaults to true.
     */
    val availableInPlanMode: Boolean
        get() = true

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
