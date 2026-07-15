package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.FunctionDescription
import net.dontdrinkandroot.sara.WebFetchClient

/**
 * Tool for fetching the content of a web page and returning it to the LLM.
 *
 * By default the HTML is converted to Markdown to preserve semantic structure (headings, links, lists, code)
 * while stripping boilerplate (scripts, navigation, forms). The [format] parameter lets the model request
 * raw HTML or plain text if needed. The output is truncated to [maxLength] characters to protect the context window.
 */
class WebFetchTool(
    private val client: WebFetchClient,
) : ToolExecutor {
    override val name: String = "web_fetch"
    override val description: String = "Fetch the content of a web page and return it as Markdown, text, or HTML"
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
                        put("description", JsonPrimitive("The absolute URL of the web page to fetch"))
                    })
                    put("format", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", kotlinx.serialization.json.buildJsonArray {
                            add(JsonPrimitive("markdown"))
                            add(JsonPrimitive("text"))
                            add(JsonPrimitive("html"))
                        })
                        put(
                            "description",
                            JsonPrimitive("Output format: 'markdown' (default, preserves structure), 'text' (plain text), or 'html' (raw HTML)")
                        )
                    })
                    put("max_length", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Maximum number of characters to return (default 50000)"))
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("url"))
                })
            }
        )
    }

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val url = arguments["url"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: url")

        val format = arguments["format"]?.jsonPrimitive?.content ?: "markdown"
        if (format !in setOf("markdown", "text", "html")) {
            return ToolResult.Error("Invalid format '$format'. Must be one of: markdown, text, html")
        }

        val maxLength = arguments["max_length"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: DEFAULT_MAX_LENGTH

        if (verbose) {
            println("[sara] Fetching: $url (format=$format, maxLength=$maxLength)")
        }

        return try {
            val response = client.fetch(url)
            if (verbose) {
                println("[sara] Fetched ${response.body.length} characters (content-type: ${response.contentType})")
            }

            val contentType = response.contentType?.lowercase() ?: ""
            val isHtml = contentType.startsWith("text/html") ||
                    contentType.startsWith("application/xhtml") ||
                    response.body.trimStart().startsWith("<")

            val content = when {
                format == "html" -> response.body
                format == "text" && isHtml -> {
                    val doc = com.fleeksoft.ksoup.Ksoup.parse(response.body, url)
                    doc.body().text()
                }

                format == "markdown" && isHtml -> HtmlToMarkdown.convert(response.body, url)
                else -> response.body
            }

            val title = if (isHtml) HtmlToMarkdown.extractTitle(response.body) else ""
            val truncated = truncate(content, maxLength)

            val output = buildString {
                append("URL: ").append(response.url).append('\n')
                if (title.isNotBlank()) append("Title: ").append(title).append('\n')
                append('\n')
                append(truncated)
            }

            ToolResult.Success(output)
        } catch (e: Exception) {
            ToolResult.Error("Failed to fetch web page: ${e.message}")
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
