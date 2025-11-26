package net.dontdrinkandroot.sara.tool

import ToolExecutor
import ToolResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.FunctionDescription
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite

/**
 * Tool for writing a string into a file (overwrites existing content or creates the file).
 */
class WriteFileTool : ToolExecutor {
    override val name: String = "write_file"
    override val description: String = "Write content to a file (overwrites if it exists)"

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

    override fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val path = arguments["path"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: path")
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: content")

        return try {
            if (verbose) println("[sara] Writing file: $path (${content.length} chars)")
            val written = writeWholeFile(path, content)
            ToolResult.Success("Wrote $written bytes to $path")
        } catch (e: Exception) {
            ToolResult.Error("Failed to write file '$path': ${e.message}")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeWholeFile(path: String, content: String): Long {
        val file = fopen(path, "w") ?: throw RuntimeException("Unable to open file for writing")
        try {
            var totalWritten = 0L
            val bytes = content.encodeToByteArray()
            bytes.usePinned { pinned ->
                val written = fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
                totalWritten += written.toLong()
            }
            if (fflush(file) != 0) throw RuntimeException("Failed to flush file buffer")
            return totalWritten
        } finally {
            fclose(file)
        }
    }
}
