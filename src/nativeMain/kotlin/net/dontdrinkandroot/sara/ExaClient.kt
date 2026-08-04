package net.dontdrinkandroot.sara

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for the Exa Search and Contents APIs.
 *
 * Authenticates via `Authorization: Bearer <apiKey>`. Search uses highlights mode (the Exa-recommended
 * content mode for agent workflows), content retrieval returns clean markdown text.
 */
class ExaClient(
    private val apiKey: String,
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                explicitNulls = false
            })
        }
    }

    /**
     * Searches the web via the Exa Search API and returns ranked results with highlights.
     */
    suspend fun search(query: String, numResults: Int = 10): List<ExaSearchResult> {
        val response: HttpResponse = client.post("${BASE_URL}/search") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                SearchRequest(
                    query = query,
                    type = "auto",
                    numResults = numResults,
                    contents = SearchContents(highlights = true)
                )
            )
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw ExaApiException("Exa search API error: ${response.status} - $errorBody")
        }

        return response.body<ExaSearchResponse>().results
    }

    /**
     * Retrieves the content of a web page via the Exa Contents API and returns it as markdown text.
     *
     * @throws ExaApiException if the request fails or the URL reports an error status.
     */
    suspend fun getContents(url: String, maxCharacters: Int): ExaContentResult {
        val response: HttpResponse = client.post("${BASE_URL}/contents") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                ContentsRequest(
                    urls = listOf(url),
                    text = ContentsText(maxCharacters = maxCharacters)
                )
            )
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw ExaApiException("Exa contents API error: ${response.status} - $errorBody")
        }

        val body: ExaContentsResponse = response.body()

        val status = body.statuses.firstOrNull { it.id == url }
        if (status?.status != "success") {
            val errorTag = status?.error?.tag
            throw ExaApiException("Exa contents error for $url${errorTag?.let { " - $it" }.orEmpty()}")
        }

        return body.results.firstOrNull { it.url == url }
            ?: throw ExaApiException("Exa contents returned no result for $url")
    }

    fun close() {
        client.close()
    }

    @Serializable
    private data class SearchRequest(
        val query: String,
        val type: String,
        val numResults: Int,
        val contents: SearchContents,
    )

    @Serializable
    private data class SearchContents(
        val highlights: Boolean,
    )

    @Serializable
    private data class ContentsRequest(
        val urls: List<String>,
        val text: ContentsText,
    )

    @Serializable
    private data class ContentsText(
        val maxCharacters: Int,
    )

    companion object {
        const val BASE_URL = "https://api.exa.ai"
    }
}

class ExaApiException(message: String) : Exception(message)

@Serializable
data class ExaSearchResponse(
    val results: List<ExaSearchResult> = emptyList(),
)

@Serializable
data class ExaSearchResult(
    val title: String? = null,
    val url: String? = null,
    val publishedDate: String? = null,
    val highlights: List<String> = emptyList(),
)

@Serializable
data class ExaContentsResponse(
    val results: List<ExaContentResult> = emptyList(),
    val statuses: List<ExaStatus> = emptyList(),
)

@Serializable
data class ExaContentResult(
    val title: String? = null,
    val url: String? = null,
    val text: String? = null,
)

@Serializable
data class ExaStatus(
    val id: String? = null,
    val status: String? = null,
    val error: ExaStatusError? = null,
)

@Serializable
data class ExaStatusError(
    val tag: String? = null,
)
