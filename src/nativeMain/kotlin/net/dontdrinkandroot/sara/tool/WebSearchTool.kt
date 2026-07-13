package net.dontdrinkandroot.sara.tool

import ToolExecutor
import ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.dontdrinkandroot.sara.FunctionDescription
import net.dontdrinkandroot.sara.SearxngClient

/**
 * Tool for searching the web via a configured Searxng instance and returning the results to the LLM.
 */
class WebSearchTool(
    private val searxngClient: SearxngClient,
) : ToolExecutor {
    override val name: String = "web_search"
    override val description: String = "Search the web and return the results"
    override val isSafe: Boolean = true

    override fun getFunctionDescription(): FunctionDescription {
        return FunctionDescription(
            name = name,
            description = description,
            parameters = kotlinx.serialization.json.buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("query", kotlinx.serialization.json.buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The search query"))
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("query"))
                })
            }
        )
    }

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: query")

        if (verbose) {
            println("[sara] Searching: $query")
        }

        return try {
            val results = searxngClient.search(query)
            if (verbose) {
                println("[sara] Search returned ${results.size} result(s)")
            }
            ToolResult.Success(formatResults(query, results))
        } catch (e: Exception) {
            ToolResult.Error("Failed to search: ${e.message}")
        }
    }

    private fun formatResults(query: String, results: List<net.dontdrinkandroot.sara.SearxngResult>): String {
        val meaningful = results.filter { it.url != null || it.title != null }
        if (meaningful.isEmpty()) return "No results found for: $query"

        return buildString {
            meaningful.forEachIndexed { index, result ->
                if (index > 0) append("\n\n")
                append("## Result ${index + 1}\n")
                result.title?.takeIf(String::isNotBlank)?.let { append("Title: ").append(it).append('\n') }
                result.url?.takeIf(String::isNotBlank)?.let { append("URL: ").append(it).append('\n') }
                result.content?.takeIf(String::isNotBlank)?.let { append("Snippet: ").append(it).append('\n') }
            }
        }.trimEnd()
    }
}
