package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.*
import net.dontdrinkandroot.sara.ExaClient
import net.dontdrinkandroot.sara.FunctionDescription

/**
 * Tool for extracting clean, LLM-ready content from a web page via the Exa Contents API.
 *
 * Exa handles JavaScript-rendered pages, PDFs, and complex layouts and returns the content as
 * markdown text. The output is truncated to [DEFAULT_MAX_LENGTH] characters to protect the context window.
 */
class ExaContentsTool(
    private val client: ExaClient,
) : ToolExecutor {
    override val name: String = "exa_contents"
    override val description: String = "Extract clean, LLM-ready content from a web page via Exa"
    override val isSafe: Boolean = true

    override fun getFunctionDescription(): FunctionDescription {
        return FunctionDescription(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    put("url", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The absolute URL of the web page to extract content from"))
                    })
                    put("max_length", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Maximum number of characters to return (default 50000)"))
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("url"))
                })
            }
        )
    }

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val url = arguments["url"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: url")

        val maxLength = arguments["max_length"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: DEFAULT_MAX_LENGTH

        if (verbose) {
            println("[sara] Exa fetching contents: $url (maxLength=$maxLength)")
        }

        return try {
            val result = client.getContents(url, maxLength)
            val truncated = truncate(result.text.orEmpty(), maxLength)

            val output = buildString {
                append("URL: ").append(result.url).append('\n')
                result.title?.takeIf(String::isNotBlank)?.let { append("Title: ").append(it).append('\n') }
                append('\n')
                append(truncated)
            }

            ToolResult.Success(output)
        } catch (e: Exception) {
            ToolResult.Error("Failed to extract contents with Exa: ${e.message}")
        }
    }

    private fun truncate(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        return buildString {
            append(text.substring(0, maxLength))
            append("\n\n...[truncated]")
        }
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 50000
    }
}
