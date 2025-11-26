package net.dontdrinkandroot.sara.tool

import ToolExecutor
import ToolResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.FunctionDescription
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen

/**
 * Tool for reading the entire content of a file as a string.
 */
class ReadFileTool : ToolExecutor {
    override val name: String = "read_file"
    override val description: String = "Read the content of a file and return it as a string"

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
                })
                put("required", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("path"))
                })
            }
        )
    }

    override fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val path = arguments["path"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: path")

        return try {
            if (verbose) println("[sara] Reading file: $path")
            val content = readWholeFile(path)
            ToolResult.Success(content)
        } catch (e: Exception) {
            ToolResult.Error("Failed to read file '$path': ${e.message}")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readWholeFile(path: String): String = memScoped {
        val file = fopen(path, "r") ?: throw RuntimeException("Unable to open file for reading")
        try {
            val sb = StringBuilder()
            val buffer = ByteArray(4096)
            while (true) {
                val line = fgets(buffer.refTo(0), buffer.size, file) ?: break
                sb.append(line.toKString())
            }
            sb.toString()
        } finally {
            fclose(file)
        }
    }
}
