package net.dontdrinkandroot.sara

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Fetches web pages and returns their raw content along with metadata (content type, final URL).
 *
 * Uses a realistic browser User-Agent so that common sites do not reject the request.
 */
class WebFetchClient {

    private val client = HttpClient()

    /**
     * Fetches the content at [url] and returns the response body as text along with the content type.
     *
     * @param url Absolute URL to fetch.
     * @return The fetched page content.
     * @throws WebFetchException if the request fails or returns a non-success status.
     */
    suspend fun fetch(url: String): WebFetchResponse {
        val response: HttpResponse = try {
            client.get(url) {
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                header(
                    HttpHeaders.Accept,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7"
                )
            }
        } catch (e: Exception) {
            throw WebFetchException("Failed to fetch $url: ${e.message}")
        }

        if (!response.status.isSuccess()) {
            val errorBody = try {
                response.bodyAsText()
            } catch (_: Exception) {
                ""
            }
            throw WebFetchException("HTTP ${response.status.value} for $url${if (errorBody.isNotBlank()) " - $errorBody" else ""}")
        }

        val contentType = response.contentType()?.toString()
        val body = response.bodyAsText()

        return WebFetchResponse(
            url = url,
            body = body,
            contentType = contentType,
        )
    }

    fun close() {
        client.close()
    }
}

class WebFetchException(message: String) : Exception(message)

data class WebFetchResponse(
    val url: String,
    val body: String,
    val contentType: String?,
)
