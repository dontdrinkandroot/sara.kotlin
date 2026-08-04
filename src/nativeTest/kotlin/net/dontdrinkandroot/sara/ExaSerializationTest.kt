package net.dontdrinkandroot.sara

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ExaSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSearchResponseParsing() {
        val sample = """
            {
              "requestId": "b5947044c4b78efa9552a7c89b306d95",
              "searchType": "auto",
              "results": [
                {
                  "title": "Page Title",
                  "url": "https://example.com/page",
                  "id": "https://example.com/page",
                  "publishedDate": "2024-01-15T00:00:00.000Z",
                  "author": "Author Name",
                  "image": "https://example.com/image.png",
                  "favicon": "https://example.com/favicon.ico",
                  "text": "Full page content as markdown...",
                  "highlights": ["Key excerpt one", "Key excerpt two"],
                  "highlightScores": [0.46],
                  "subpages": [],
                  "extras": {"links": ["https://example.com/related"]}
                }
              ],
              "output": {"content": "Synthesized answer", "grounding": []},
              "costDollars": {"total": 0.007}
            }
        """.trimIndent()

        val response = json.decodeFromString<ExaSearchResponse>(sample)

        assertEquals(1, response.results.size)
        val result = response.results[0]
        assertEquals("Page Title", result.title)
        assertEquals("https://example.com/page", result.url)
        assertEquals("2024-01-15T00:00:00.000Z", result.publishedDate)
        assertEquals(listOf("Key excerpt one", "Key excerpt two"), result.highlights)
    }

    @Test
    fun testSearchResponseWithEmptyResults() {
        val sample = """{"requestId": "abc", "results": []}"""

        val response = json.decodeFromString<ExaSearchResponse>(sample)

        assertEquals(0, response.results.size)
    }

    @Test
    fun testContentsResponseParsing() {
        val sample = """
            {
              "requestId": "e492118ccdedcba5088bfc4357a8a125",
              "results": [
                {
                  "title": "Page Title",
                  "url": "https://example.com/page",
                  "id": "https://example.com/page",
                  "text": "Clean markdown content",
                  "highlights": ["Key excerpt"],
                  "subpages": []
                }
              ],
              "statuses": [
                {"id": "https://example.com/page", "status": "success"}
              ],
              "costDollars": {"total": 0.003}
            }
        """.trimIndent()

        val response = json.decodeFromString<ExaContentsResponse>(sample)

        assertEquals(1, response.results.size)
        assertEquals("Page Title", response.results[0].title)
        assertEquals("Clean markdown content", response.results[0].text)
        assertEquals(1, response.statuses.size)
        assertEquals("success", response.statuses[0].status)
    }

    @Test
    fun testContentsResponseWithErrorStatus() {
        val sample = """
            {
              "results": [],
              "statuses": [
                {
                  "id": "https://example.com/maybe-broken",
                  "status": "error",
                  "error": {"tag": "CRAWL_NOT_FOUND", "httpStatusCode": 404}
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ExaContentsResponse>(sample)

        assertEquals(0, response.results.size)
        val status = response.statuses[0]
        assertEquals("error", status.status)
        assertEquals("CRAWL_NOT_FOUND", status.error?.tag)
    }
}
