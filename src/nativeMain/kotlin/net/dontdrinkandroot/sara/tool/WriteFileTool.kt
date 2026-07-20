package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.FunctionDescription
import net.dontdrinkandroot.sara.extensions.writeWholeFile

/**
 * Tool for writing a string into a file (overwrites existing content or creates the file).
 */
class WriteFileTool : ToolExecutor {
    override val name: String = "write_file"
    override val description: String = "Write content to a file (overwrites if it exists)"
    override val availableInPlanMode: Boolean = false

    override fun getFunctionDescription(): FunctionDescription {
        return FunctionDescription(
            name = name,
            description = description,
            parameters = kotlinx.serialization.json.buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("path", kotlinx.serialization.json.buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Absolute or relative path to the file"))
                    })
                    put("content", kotlinx.serialization.json.buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The content to write to the file"))
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("path"))
                    add(JsonPrimitive("content"))
                })
            }
        )
    }

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val path = arguments["path"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: path")
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: content")

        return try {
            if (verbose) println("[sara] Writing file: $path (${content.length} chars)")
            writeWholeFile(path, content)
            ToolResult.Success("Wrote ${content.encodeToByteArray().size} bytes to $path")
        } catch (e: Exception) {
            ToolResult.Error("Failed to write file '$path': ${e.message}")
        }
    }
}
