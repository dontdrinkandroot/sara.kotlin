package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.*
import net.dontdrinkandroot.sara.ExaClient
import net.dontdrinkandroot.sara.ExaSearchResult
import net.dontdrinkandroot.sara.FunctionDescription

/**
 * Tool for searching the web via the Exa Search API and returning ranked results with highlights to the LLM.
 */
class ExaSearchTool(
    private val client: ExaClient,
) : ToolExecutor {
    override val name: String = "exa_search"
    override val description: String = "Search the web with Exa and return ranked results with highlights"
    override val isSafe: Boolean = true

    override fun getFunctionDescription(): FunctionDescription {
        return FunctionDescription(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The search query"))
                    })
                    put("num_results", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Number of results to return (default 10)"))
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("query"))
                })
            }
        )
    }

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: query")

        val numResults = arguments["num_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_NUM_RESULTS

        if (verbose) {
            println("[sara] Exa searching: $query (numResults=$numResults)")
        }

        return try {
            val results = client.search(query, numResults)
            if (verbose) {
                println("[sara] Exa search returned ${results.size} result(s)")
            }
            ToolResult.Success(formatResults(query, results))
        } catch (e: Exception) {
            ToolResult.Error("Failed to search with Exa: ${e.message}")
        }
    }

    private fun formatResults(query: String, results: List<ExaSearchResult>): String {
        val meaningful = results.filter { it.url != null || it.title != null }
        if (meaningful.isEmpty()) return "No results found for: $query"

        return buildString {
            meaningful.forEachIndexed { index, result ->
                if (index > 0) append("\n\n")
                append("## Result ${index + 1}\n")
                result.title?.takeIf(String::isNotBlank)?.let { append("Title: ").append(it).append('\n') }
                result.url?.takeIf(String::isNotBlank)?.let { append("URL: ").append(it).append('\n') }
                result.publishedDate?.takeIf(String::isNotBlank)?.let { append("Published: ").append(it).append('\n') }
                result.highlights
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach { append("Highlight: ").append(it).append('\n') }
            }
        }.trimEnd()
    }

    companion object {
        const val DEFAULT_NUM_RESULTS = 10
    }
}
