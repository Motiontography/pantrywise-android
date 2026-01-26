package com.pantrywise.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for OpenAI API
 */
interface OpenAIApi {

    companion object {
        const val BASE_URL = "https://api.openai.com/v1/"
    }

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIChatRequest
    ): Response<OpenAIChatResponse>
}

// Request model
data class OpenAIChatRequest(
    val model: String = "gpt-5.2",
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.1,
    val max_completion_tokens: Int = 2000
)

data class OpenAIMessage(
    val role: String,
    val content: Any // Can be String or List<OpenAIContentPart> for vision
)

data class OpenAIContentPart(
    val type: String,
    val text: String? = null,
    val image_url: OpenAIImageUrl? = null
)

data class OpenAIImageUrl(
    val url: String,
    val detail: String = "high"
)

// Response models
data class OpenAIChatResponse(
    val choices: List<OpenAIChoice>
)

data class OpenAIChoice(
    val message: OpenAIResponseMessage
)

data class OpenAIResponseMessage(
    val content: String
)

data class OpenAIErrorResponse(
    val error: OpenAIError
)

data class OpenAIError(
    val message: String,
    val type: String?
)
