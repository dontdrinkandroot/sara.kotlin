@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package net.dontdrinkandroot.sara

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * OpenAI-compatible client for chat completions.
 *
 * Notes on provider-specific features:
 * - Parameters `plugins`, `enableWebSearch`, `webSearchEngine`, and `webSearchMaxResults` are specific to OpenRouter
 *   and may be ignored by other providers.
 */
class LlmClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val siteUrl: String? = null,
    private val siteTitle: String? = null
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
     * Sends a chat completion request to the configured OpenAI-compatible API.
     *
     * Provider-specific notes:
     * - `enableWebSearch`, `webSearchEngine`, `webSearchMaxResults` map to OpenRouter's web search plugin.
     *   Other providers will likely ignore these parameters.
     *
     * @param model The model to use (e.g., "openai/gpt-4o")
     * @param messages The conversation messages
     * @param enableWebSearch If true, enables web search via the web plugin (OpenRouter-specific)
     * @param webSearchEngine Optional: "native", "exa", or null for default behavior (OpenRouter-specific)
     * @param webSearchMaxResults Maximum number of web search results (OpenRouter-specific)
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature (0-2)
     * @param topP Nucleus sampling parameter
     * @param frequencyPenalty Frequency penalty (-2 to 2)
     * @param presencePenalty Presence penalty (-2 to 2)
     * @param tools Optional tools for function calling
     * @param toolChoice Optional tool choice strategy
     * @return The completion response
     */
    suspend fun chatCompletion(
        model: String,
        messages: List<Message>,
        enableWebSearch: Boolean = false,
        webSearchEngine: String? = null,
        webSearchMaxResults: Int? = null,
        maxTokens: Int? = null,
        temperature: Double? = null,
        topP: Double? = null,
        frequencyPenalty: Double? = null,
        presencePenalty: Double? = null,
        tools: List<Tool>? = null,
        toolChoice: ToolChoice? = null
    ): ChatCompletionResponse {
        val plugins = if (enableWebSearch) {
            listOf(
                Plugin(
                    id = "web",
                    engine = webSearchEngine,
                    maxResults = webSearchMaxResults
                )
            )
        } else {
            null
        }

        val request = ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = false,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            frequencyPenalty = frequencyPenalty,
            presencePenalty = presencePenalty,
            tools = tools,
            toolChoice = toolChoice,
            plugins = plugins
        )

        val response: HttpResponse = client.post("${baseUrl}/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            siteUrl?.let { header("HTTP-Referer", it) }
            siteTitle?.let { header("X-Title", it) }
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw LlmApiException("API error: ${response.status} - $errorBody")
        }

        return response.body()
    }

    fun close() {
        client.close()
    }
}

class LlmApiException(message: String) : Exception(message)

// Request/Response Data Classes

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: ToolChoice? = null,
    val plugins: List<Plugin>? = null
)

@Serializable
data class Plugin(
    val id: String,
    val engine: String? = null,
    @SerialName("max_results") val maxResults: Int? = null,
    @SerialName("search_prompt") val searchPrompt: String? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
data class Tool(
    @EncodeDefault val type: String = "function",
    val function: FunctionDescription
)

@Serializable
data class FunctionDescription(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject
)

@Serializable(with = ToolChoiceSerializer::class)
sealed class ToolChoice {
    @Serializable
    @SerialName("none")
    object None : ToolChoice()

    @Serializable
    @SerialName("auto")
    object Auto : ToolChoice()

    @Serializable
    data class Function(
        @EncodeDefault val type: String = "function",
        val function: FunctionName
    ) : ToolChoice()
}

object ToolChoiceSerializer : KSerializer<ToolChoice> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ToolChoice", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ToolChoice) {
        when (value) {
            is ToolChoice.None -> encoder.encodeString("none")
            is ToolChoice.Auto -> encoder.encodeString("auto")
            is ToolChoice.Function -> {
                val jsonEncoder = encoder as? JsonEncoder
                    ?: error("ToolChoice must be serialized with Json")
                val obj = buildJsonObject {
                    put("type", JsonPrimitive("function"))
                    put(
                        "function",
                        buildJsonObject { put("name", JsonPrimitive(value.function.name)) }
                    )
                }
                jsonEncoder.encodeJsonElement(obj)
            }
        }
    }

    override fun deserialize(decoder: Decoder): ToolChoice {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ToolChoice must be deserialized with Json")
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString -> {
                when (element.content) {
                    "none" -> ToolChoice.None
                    "auto" -> ToolChoice.Auto
                    else -> throw IllegalArgumentException("Unknown ToolChoice string: ${'$'}{element.content}")
                }
            }

            element is JsonObject -> {
                val type = element["type"]?.jsonPrimitive?.content
                if (type == "function") {
                    val fnElem = element["function"]
                        ?: throw IllegalArgumentException("ToolChoice.function missing")
                    val fnObj = fnElem.jsonObject
                    val name = fnObj["name"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("ToolChoice.function.name missing")
                    ToolChoice.Function(function = FunctionName(name))
                } else {
                    throw IllegalArgumentException("Unsupported ToolChoice object type: ${'$'}type")
                }
            }

            else -> throw IllegalArgumentException("Invalid ToolChoice JSON: ${'$'}element")
        }
    }
}

@Serializable
data class FunctionName(
    val name: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val created: Long,
    val model: String,
    val `object`: String,
    @SerialName("system_fingerprint") val systemFingerprint: String? = null,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    @SerialName("finish_reason") val finishReason: String?,
    @SerialName("native_finish_reason") val nativeFinishReason: String? = null,
    val message: Message? = null,
    val error: ErrorResponse? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class ErrorResponse(
    val code: Int,
    val message: String,
    val metadata: JsonObject? = null
)

@Serializable
data class ToolCall(
    val id: String,
    @EncodeDefault val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)
