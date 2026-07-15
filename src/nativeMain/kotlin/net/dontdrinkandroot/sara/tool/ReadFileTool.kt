package net.dontdrinkandroot.sara.tool

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.*
import net.dontdrinkandroot.sara.FunctionDescription
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen

/**
 * Tool for reading a file as a string, starting at a character offset and limited to a number of characters.
 */
class ReadFileTool : ToolExecutor {
    override val name: String = "read_file"
    override val description: String =
        "Read the content of a file and return it as a string. Reads up to 'limit' characters starting at 'offset'."
    override val isSafe: Boolean = true

    override fun getFunctionDescription(): FunctionDescription {
        return FunctionDescription(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    put("path", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Absolute or relative path to the file"))
                    })
                    put("offset", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put(
                            "description",
                            JsonPrimitive("Character offset to start reading at (0-based, default 0)")
                        )
                        put("minimum", JsonPrimitive(0))
                    })
                    put("limit", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put(
                            "description",
                            JsonPrimitive("Maximum number of characters to read (default 10000)")
                        )
                        put("minimum", JsonPrimitive(1))
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("path"))
                })
            }
        )
    }

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val path = arguments["path"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: path")

        val offset = arguments["offset"]?.jsonPrimitive?.content?.toIntOrNull()
        if (arguments["offset"] != null && (offset == null || offset < 0)) {
            return ToolResult.Error("Parameter 'offset' must be a non-negative integer (0-based)")
        }
        val limit = arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull()
        if (arguments["limit"] != null && (limit == null || limit < 1)) {
            return ToolResult.Error("Parameter 'limit' must be a positive integer")
        }

        val startOffset = offset ?: 0
        val maxChars = limit ?: DEFAULT_LIMIT

        return try {
            if (verbose) println("[sara] Reading file: $path (offset=$startOffset, limit=$maxChars)")
            val content = readRange(path, startOffset, maxChars)
            ToolResult.Success(content)
        } catch (e: Exception) {
            ToolResult.Error("Failed to read file '$path': ${e.message}")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readRange(path: String, offset: Int, limit: Int): String = memScoped {
        val file = fopen(path, "r") ?: throw RuntimeException("Unable to open file for reading")
        try {
            val sb = StringBuilder()
            val buffer = ByteArray(4096)
            while (true) {
                val line = fgets(buffer.refTo(0), buffer.size, file) ?: break
                sb.append(line.toKString())
            }
            val full = sb.toString()
            val total = full.length

            if (offset >= total) {
                return "[offset $offset is at or beyond end of file ($total characters)]"
            }

            val end = minOf(offset + limit, total)
            val slice = full.substring(offset, end)

            if (end < total) {
                val nextOffset = end
                "$slice...[truncated, continue with offset=$nextOffset, file has $total characters]"
            } else {
                slice
            }
        } finally {
            fclose(file)
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 10000
    }
}
