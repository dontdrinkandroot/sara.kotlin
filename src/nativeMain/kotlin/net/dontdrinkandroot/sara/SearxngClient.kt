package net.dontdrinkandroot.sara

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for a Searxng instance's JSON search API (`/search?format=json`).
 *
 * An optional bearer token can be supplied via [token] for instances that require authentication.
 */
class SearxngClient(
    private val baseUrl: String,
    private val token: String? = null,
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
     * Queries the Searxng instance and returns all results from a single request page.
     */
    suspend fun search(query: String): List<SearxngResult> {
        val response: HttpResponse = client.get("${baseUrl}/search") {
            parameter("format", "json")
            parameter("q", query)
            token?.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw SearxngApiException("Searxng API error: ${response.status} - $errorBody")
        }

        val body: SearxngResponse = response.body()
        return body.results
    }

    fun close() {
        client.close()
    }
}

class SearxngApiException(message: String) : Exception(message)

@Serializable
data class SearxngResponse(
    val query: String? = null,
    val results: List<SearxngResult> = emptyList(),
    @SerialName("number_of_results") val numberOfResults: Int? = null,
)

@Serializable
data class SearxngResult(
    val url: String? = null,
    val title: String? = null,
    val content: String? = null,
    val engine: String? = null,
    val score: Double? = null,
)
