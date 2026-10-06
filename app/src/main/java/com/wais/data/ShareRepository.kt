package com.wais.data

import android.net.Uri
import android.util.Log
import com.wais.BuildConfig
import com.wais.domain.model.AiSource
import com.wais.network.jina.JinaApi
import com.wais.network.linkup.LinkupApi
import com.wais.network.linkup.LinkupSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException

enum class ShareProgressStep {
    FETCHING,
    EXTRACTING,
    SEARCHING,
    DONE
}

class ShareRepository(
    private val jinaApi: JinaApi,
    private val claimExtractor: ClaimExtractor,
    private val linkupApi: LinkupApi
) {
    private val tag = "ShareRepo"

    data class Result(
        val verdict: String,
        val summary: String,
        val sources: List<AiSource>,
        val error: String? = null
    )

    suspend fun analyze(url: String): Result {
        if (BuildConfig.JINA_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "Jina API key missing. Set JINA_API_KEY in local.properties.",
                sources = emptyList(),
                error = "Missing JINA_API_KEY"
            )
        }

        if (BuildConfig.GEMINI_API_KEY.isBlank() && BuildConfig.OPENROUTER_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "No AI key configured. Set GEMINI_API_KEY (or OPENROUTER_API_KEY) in local.properties.",
                sources = emptyList(),
                error = "Missing GEMINI_API_KEY"
            )
        }

        if (BuildConfig.LINKUP_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "Linkup API key missing. Set LINKUP_API_KEY in local.properties.",
                sources = emptyList(),
                error = "Missing LINKUP_API_KEY"
            )
        }

        Log.d(tag, "Fetching URL content: $url")

        val pageContent = fetchPageContent(url)
        if (pageContent.isBlank()) {
            return Result(
                verdict = "UNABLE TO FETCH",
                summary = "Could not fetch content from the shared URL.",
                sources = emptyList(),
                error = "Empty page content"
            )
        }

        Log.d(tag, "Page content fetched (${pageContent.length} chars): ${pageContent.take(300)}")

        val claim = extractClaim(pageContent)
        Log.d(tag, "Extracted claim: $claim")

        if (claim.isBlank()) {
            return Result(
                verdict = "UNABLE TO EXTRACT",
                summary = "Could not identify a clear claim in this content.",
                sources = emptyList(),
                error = "No claim extracted"
            )
        }

        val answer = searchSources(claim)
        val verdict = classifyVerdict(answer)
        val summary = enforceFiveSentences(answer)
        val sources = listOf(
            AiSource(title = Uri.parse(url).host ?: "Source", url = url)
        )

        return Result(
            verdict = verdict,
            summary = summary,
            sources = sources
        )
    }

    suspend fun analyzeWithProgress(url: String, onProgress: (ShareProgressStep) -> Unit): Result {
        if (BuildConfig.JINA_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "Jina API key missing. Set JINA_API_KEY in local.properties.",
                sources = emptyList(),
                error = "Missing JINA_API_KEY"
            )
        }

        if (BuildConfig.GEMINI_API_KEY.isBlank() && BuildConfig.OPENROUTER_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "No AI key configured. Set GEMINI_API_KEY (or OPENROUTER_API_KEY) in local.properties.",
                sources = emptyList(),
                error = "Missing GEMINI_API_KEY"
            )
        }

        if (BuildConfig.LINKUP_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "Linkup API key missing. Set LINKUP_API_KEY in local.properties.",
                sources = emptyList(),
                error = "Missing LINKUP_API_KEY"
            )
        }

        withContext(Dispatchers.Main.immediate) { onProgress(ShareProgressStep.FETCHING) }
        Log.d(tag, "Fetching URL content: $url")

        val pageContent = fetchPageContent(url)
        if (pageContent.isBlank() || pageContent == url) {
            withContext(Dispatchers.Main.immediate) { onProgress(ShareProgressStep.EXTRACTING) }
            Log.d(tag, "Using URL as content since fetch failed")
        } else {
            Log.d(tag, "Page content fetched (${pageContent.length} chars): ${pageContent.take(200)}")
        }

        withContext(Dispatchers.Main.immediate) { onProgress(ShareProgressStep.EXTRACTING) }
        val claim = extractClaim(pageContent)
        Log.d(tag, "Extracted claim: $claim")

        if (claim.isBlank()) {
            return Result(
                verdict = "UNABLE TO EXTRACT",
                summary = "Could not identify a clear claim in this content.",
                sources = emptyList(),
                error = "No claim extracted"
            )
        }

        withContext(Dispatchers.Main.immediate) { onProgress(ShareProgressStep.SEARCHING) }
        val answer = searchSources(claim)
        val verdict = classifyVerdict(answer)
        val summary = enforceFiveSentences(answer)
        val sources = listOf(
            AiSource(title = Uri.parse(url).host ?: "Source", url = url)
        )

        withContext(Dispatchers.Main.immediate) { onProgress(ShareProgressStep.DONE) }
        return Result(
            verdict = verdict,
            summary = summary,
            sources = sources
        )
    }

    private suspend fun fetchPageContent(url: String): String {
        val jinaUrl = "https://r.jina.ai/$url"
        
        return try {
            val response = jinaApi.fetchContent(
                url = jinaUrl,
                authorization = "Bearer ${BuildConfig.JINA_API_KEY}"
            )
            
            Log.d(tag, "Jina response code: ${response.code}")
            
            if (response.code == 200 && response.data != null) {
                val content = response.data.content?.trim()
                    ?: response.data.description?.trim()
                    ?: ""
                
                if (content.isNotEmpty()) {
                    Log.d(tag, "Content fetched (${content.length} chars): ${content.take(150)}")
                    return content
                }
            }
            
            val rawText = response.text?.trim().orEmpty()
            if (rawText.isNotEmpty() && !rawText.startsWith("https://")) {
                Log.d(tag, "Using raw text (${rawText.length} chars): ${rawText.take(150)}")
                return rawText
            }
            
            Log.w(tag, "Empty/invalid content from Jina, using URL as content")
            url
        } catch (e: Exception) {
            Log.e(tag, "Jina fetch failed: ${e.message}")
            url
        }
    }

    private suspend fun extractClaim(content: String): String {
        val prompt = """Extract the main fact-checkable claim from this article/page content.

RULES:
- Focus on the core claim, statement, or assertion being made
- Ignore author names, publication dates, website names
- Ignore navigation, menus, footers
- Focus on specific claims, statistics, quotes, or statements

EXTRACT:
- The main claim or assertion
- Specific facts or statistics mentioned
- Direct quotes from people
- News or events being reported

CONTENT:
$content

OUTPUT: Return ONLY the main claim as one paragraph. If no fact-checkable claim found, write exactly: NO_CLAIM_FOUND"""

        return claimExtractor.extract(prompt) {
            content.lines().filter { it.length > 30 }.take(3).joinToString(" ").take(500)
        }
    }

    private suspend fun searchSources(claim: String): String {
        val query = buildString {
            append("Verify the following claim and provide a sourced answer.\n\n")
            append("Claim: $claim\n\n")
            append("Format:\n")
            append("Sentence 1: Verdict (TRUE/FALSE/MISLEADING/UNVERIFIED)\n")
            append("Sentence 2-3: Key evidence from credible sources\n")
            append("Sentence 4: Important context or caveats\n")
            append("Sentence 5: Final recommendation\n")
            append("Keep factual, cite sources where possible.")
        }.take(1200)

        val request = LinkupSearchRequest(
            q = query,
            depth = "standard",
            outputType = "sourcedAnswer",
            includeImages = false
        )

        return requestWithRetry(request)?.answer?.trim().orEmpty()
    }

    private suspend fun requestWithRetry(request: LinkupSearchRequest): com.wais.network.linkup.LinkupSearchResponse? {
        val cooldownMs = listOf(0L, 1200L, 3000L, 6000L)

        for (i in cooldownMs.indices) {
            val wait = cooldownMs[i]
            if (wait > 0) delay(wait)

            try {
                return linkupApi.search(
                    authorization = "Bearer ${BuildConfig.LINKUP_API_KEY}",
                    request = request
                )
            } catch (e: HttpException) {
                if (e.code() !in listOf(429, 500, 502, 503, 504)) return null
            } catch (t: Throwable) {
                Log.w(tag, "attempt=${i + 1} failed: ${t.message}")
            }
        }
        return null
    }

    private fun classifyVerdict(answer: String): String {
        val lower = answer.lowercase()
        return when {
            lower.contains("false") || lower.contains("incorrect") || lower.contains("not true") -> "FALSE"
            lower.contains("misleading") || lower.contains("missing context") || lower.contains("partially") -> "MISLEADING"
            lower.contains("true") || lower.contains("accurate") || lower.contains("verified") -> "TRUE"
            else -> "UNVERIFIED"
        }
    }

    private fun enforceFiveSentences(answer: String): String {
        val fallback = listOf(
            "This claim needs verification from reliable web sources.",
            "Available evidence is limited or mixed in the retrieved context.",
            "Some details may be incomplete without the original full context.",
            "Use at least one trusted source before drawing conclusions.",
            "Check the linked source before sharing this content."
        )

        val cleaned = answer
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\[[^\\]]*]"), "")
            .trim()

        val split = Regex("[^.!?]+[.!?]?")
            .findAll(cleaned)
            .map { it.value.trim() }
            .filter { it.length > 4 }
            .map { ensurePeriod(it) }
            .toList()

        return when {
            split.size >= 5 -> split.take(5).joinToString(" ")
            split.size >= 3 -> (split + fallback.take(5 - split.size)).joinToString(" ")
            split.isNotEmpty() -> (split + fallback).joinToString(" ")
            else -> fallback.joinToString(" ")
        }
    }

    private fun ensurePeriod(text: String): String {
        return if (text.endsWith('.') || text.endsWith('!') || text.endsWith('?')) {
            text
        } else {
            "$text."
        }
    }
}
