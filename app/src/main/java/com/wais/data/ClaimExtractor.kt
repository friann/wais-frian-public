package com.wais.data

import android.util.Log
import com.wais.BuildConfig
import com.wais.network.gemini.GeminiApi
import com.wais.network.gemini.GeminiContent
import com.wais.network.gemini.GeminiGenerationConfig
import com.wais.network.gemini.GeminiInlineData
import com.wais.network.gemini.GeminiPart
import com.wais.network.gemini.GeminiRequest
import com.wais.network.openrouter.Message
import com.wais.network.openrouter.OpenRouterApi
import com.wais.network.openrouter.OpenRouterRequest

/**
 * Extracts one fact-checkable claim from noisy text.
 * Chain: Gemini -> OpenRouter models (in order) -> caller-supplied heuristic.
 */
class ClaimExtractor(
    private val geminiApi: GeminiApi,
    private val openRouterApi: OpenRouterApi
) {
    private val tag = "ClaimExtractor"

    suspend fun extract(prompt: String, heuristic: () -> String): String {
        extractWithGemini(prompt)?.let { return it }
        extractWithOpenRouter(prompt)?.let { return it }
        Log.w(tag, "All providers failed, using heuristic")
        return heuristic()
    }

    /**
     * Reads a screenshot (base64 JPEG) and extracts the main claim. Gemini only:
     * the OpenRouter fallback models are text-only. Returns null if no claim could be read.
     */
    suspend fun extractFromImage(prompt: String, jpegBase64: String): String? {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return null
        return try {
            Log.d(tag, "Trying Gemini image extraction: $GEMINI_MODEL")
            val response = geminiApi.generate(
                model = GEMINI_MODEL,
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt),
                                GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = jpegBase64))
                            )
                        )
                    ),
                    generationConfig = GeminiGenerationConfig()
                )
            )
            if (response.promptFeedback?.blockReason != null) {
                Log.w(tag, "Gemini blocked image prompt: ${response.promptFeedback.blockReason}")
                return null
            }
            acceptable(response.firstText())
        } catch (e: Exception) {
            Log.w(tag, "Gemini image extraction failed: ${e.message}")
            null
        }
    }

    private suspend fun extractWithGemini(prompt: String): String? {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return null
        return try {
            Log.d(tag, "Trying Gemini: $GEMINI_MODEL")
            val response = geminiApi.generate(
                model = GEMINI_MODEL,
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                    generationConfig = GeminiGenerationConfig()
                )
            )
            if (response.promptFeedback?.blockReason != null) {
                Log.w(tag, "Gemini blocked prompt: ${response.promptFeedback.blockReason}")
                return null
            }
            acceptable(response.firstText()).also {
                Log.d(tag, "Gemini extracted: ${it?.take(100)}")
            }
        } catch (e: Exception) {
            Log.w(tag, "Gemini failed: ${e.message}")
            null
        }
    }

    private suspend fun extractWithOpenRouter(prompt: String): String? {
        if (BuildConfig.OPENROUTER_API_KEY.isBlank()) return null
        for (model in OPENROUTER_MODELS) {
            try {
                Log.d(tag, "Trying model: $model")
                val response = openRouterApi.chat(
                    authorization = "Bearer ${BuildConfig.OPENROUTER_API_KEY}",
                    request = OpenRouterRequest(
                        model = model,
                        messages = listOf(Message(content = prompt)),
                        max_tokens = 500
                    )
                )
                if (response.error != null) {
                    Log.w(tag, "OpenRouter error: ${response.error.message}")
                    continue
                }
                val extracted = acceptable(response.choices?.firstOrNull()?.message?.content?.trim().orEmpty())
                if (extracted != null) return extracted
            } catch (e: Exception) {
                Log.w(tag, "Model $model failed: ${e.message}")
            }
        }
        return null
    }

    /** Returns the text if it is a usable claim, otherwise null so the next provider is tried. */
    private fun acceptable(text: String): String? {
        val trimmed = text.trim()
        return trimmed.takeIf {
            it.length > 15 && !it.contains("NO_CLAIM_FOUND", ignoreCase = true)
        }
    }

    companion object {
        const val GEMINI_MODEL = "gemini-3.5-flash-lite"

        private val OPENROUTER_MODELS = listOf(
            "arcee-ai/trinity-large-preview:free",
            "stepfun/step-3.5-flash:free",
            "qwen/qwen3-8b",
        )
    }
}
