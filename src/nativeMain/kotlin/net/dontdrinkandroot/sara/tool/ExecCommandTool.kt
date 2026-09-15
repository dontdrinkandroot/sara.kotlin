package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.FunctionDescription

/**
 * Tool for executing system commands.
 */
class ExecCommandTool : ToolExecutor {
    override val name: String = "exec_command"
    override val description: String =
        "Execute a system command (including its arguments) and return its output and exit " +
            "status; oversized output is truncated to head/tail excerpts with the full log " +
            "spilled to a temporary file"

    override fun getFunctionDescription(): FunctionDescription {
        return FunctionDescription(
            name = name,
            description = description,
            parameters = kotlinx.serialization.json.buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("command", kotlinx.serialization.json.buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The full command to execute, including any arguments"))
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("command"))
                })
            }
        )
    }

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        try {
            val command = arguments["command"]?.jsonPrimitive?.content
                ?: return ToolResult.Error("Missing required parameter: command")

            if (verbose) {
                println("[sara] Executing command: $command")
            }

            val execution = executeCommandWithStatus(command)
            val truncation = truncateHeadTail(execution.output)

            if (verbose) {
                println(
                    "[sara] Command completed: ${statusLine(execution.exitStatus)}, " +
                        "${execution.output.length} characters of output"
                )
            }

            return ToolResult.CommandResult(
                output = execution.output,
                exitStatus = execution.exitStatus,
                truncation = truncation,
            )

        } catch (e: Exception) {
            return ToolResult.Error("Failed to execute command: ${e.message}")
        }
    }
}
