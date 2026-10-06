package com.wais.network.openrouter

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApi {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String? = null,
        @Header("X-Title") xTitle: String? = null,
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}

data class OpenRouterRequest(
    val model: String = "minimax/minimax-m2.5:free",
    val messages: List<Message>,
    val max_tokens: Int = 500
)

data class Message(
    val role: String = "user",
    val content: String
)

data class OpenRouterResponse(
    val choices: List<Choice>? = null,
    val error: OpenRouterError? = null
)

data class OpenRouterError(
    val message: String? = null,
    val code: String? = null
)

data class Choice(
    val message: ResponseMessage
)

data class ResponseMessage(
    val content: String? = null
)
