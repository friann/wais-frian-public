package com.wais.data

import android.net.Uri
import android.util.Log
import com.wais.BuildConfig
import com.wais.domain.model.AiSource
import com.wais.network.linkup.LinkupApi
import com.wais.network.linkup.LinkupSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException

enum class OverlayProgressStep {
    EXTRACTING,
    SEARCHING,
    DONE
}

private const val IMAGE_CLAIM_PROMPT = """This is a screenshot of a social media post, news article, or meme.

TASK: Read the text and content in the image and extract ONLY the single main fact-checkable claim.

IGNORE:
- The Wais app itself, floating buttons, and result cards
- Profile names, usernames, timestamps, like/comment/share counts
- App buttons, menus, navigation bars, and advertisements
- Watermarks and logos

EXTRACT:
- The actual claim or statement being made, in the post text or in text written on the image
- News or events reported, direct quotes, statistics, and claims about people, policies or events
- If the image shows a scene with a caption, use the caption's claim

OUTPUT: Return ONLY the main claim as one short, self-contained sentence or paragraph. If there is no fact-checkable claim, respond with exactly: NO_CLAIM_FOUND"""

class LinkupRepository(
    private val linkupApi: LinkupApi,
    private val claimExtractor: ClaimExtractor
) {
    private val tag = "LinkupRepo"

    data class Result(
        val verdict: String,
        val summary: String,
        val sources: List<AiSource>,
        val error: String? = null,
        val claim: String? = null
    )

    suspend fun analyze(rawText: String): Result {
        if (BuildConfig.LINKUP_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "Linkup API key missing. Set LINKUP_API_KEY in local.properties.",
                sources = emptyList(),
                error = "Missing LINKUP_API_KEY"
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

        Log.d(tag, "Raw text (${rawText.length} chars): ${rawText.take(500)}")

        val mainClaim = extractMainClaim(rawText)
        Log.d(tag, "Extracted claim: $mainClaim")

        if (mainClaim.isBlank()) {
            return Result(
                verdict = "UNABLE TO EXTRACT",
                summary = "Could not identify a clear claim to verify. Please ensure you're viewing a social media post or article.",
                sources = emptyList(),
                error = "No claim extracted"
            )
        }

        val query = buildSourcingQuery(mainClaim)
        Log.d(tag, "Query sent to Linkup: ${query.take(500)}")

        val request = LinkupSearchRequest(
            q = query,
            depth = "standard",
            outputType = "sourcedAnswer",
            includeImages = false
        )

        val response = requestWithRetry(request)
            ?: return Result(
                verdict = "UNAVAILABLE",
                summary = "Web analysis unavailable right now.",
                sources = emptyList(),
                error = "Linkup failed after retries"
            )

        val answer = response.answer?.trim().orEmpty()
        val sources = response.sources
            .orEmpty()
            .mapNotNull { src ->
                val name = src.name?.trim().orEmpty()
                val url = src.url?.trim().orEmpty()
                if (name.isBlank() || !url.startsWith("http")) null else AiSource(name, url)
            }
            .distinctBy { it.url }

        val verdict = classifyVerdict(answer)
        val polishedSummary = enforceFiveSentences(answer)
        val fallbackSource = buildFallbackSource(mainClaim)
        val outputSources = if (sources.isNotEmpty()) sources else listOf(fallbackSource)

        Log.d(tag, "Final verdict: $verdict")
        return Result(
            verdict = verdict,
            summary = polishedSummary,
            sources = outputSources,
            claim = mainClaim
        )
    }

    suspend fun analyzeWithProgress(rawText: String, onProgress: (OverlayProgressStep) -> Unit): Result {
        Log.d(tag, "Raw text (${rawText.length} chars)")
        return runPipeline(onProgress) { extractMainClaim(rawText) }
    }

    /** Image flow: Gemini reads the screenshot, then the same Linkup verification runs. */
    suspend fun analyzeImageWithProgress(jpegBase64: String, onProgress: (OverlayProgressStep) -> Unit): Result {
        return runPipeline(onProgress) { claimExtractor.extractFromImage(IMAGE_CLAIM_PROMPT, jpegBase64).orEmpty() }
    }

    private suspend fun runPipeline(
        onProgress: (OverlayProgressStep) -> Unit,
        extractClaim: suspend () -> String
    ): Result {
        if (BuildConfig.LINKUP_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "Linkup API key missing.",
                sources = emptyList(),
                error = "Missing LINKUP_API_KEY"
            )
        }

        if (BuildConfig.GEMINI_API_KEY.isBlank() && BuildConfig.OPENROUTER_API_KEY.isBlank()) {
            return Result(
                verdict = "UNAVAILABLE",
                summary = "No AI key configured.",
                sources = emptyList(),
                error = "Missing GEMINI_API_KEY"
            )
        }

        withContext(Dispatchers.Main.immediate) { onProgress(OverlayProgressStep.EXTRACTING) }
        val mainClaim = extractClaim()
        Log.d(tag, "Extracted claim: $mainClaim")

        if (mainClaim.isBlank()) {
            return Result(
                verdict = "UNABLE TO EXTRACT",
                summary = "Could not identify a clear claim to verify.",
                sources = emptyList(),
                error = "No claim extracted"
            )
        }

        withContext(Dispatchers.Main.immediate) { onProgress(OverlayProgressStep.SEARCHING) }
        val query = buildSourcingQuery(mainClaim)
        Log.d(tag, "Query sent to Linkup")

        val request = LinkupSearchRequest(
            q = query,
            depth = "standard",
            outputType = "sourcedAnswer",
            includeImages = false
        )

        val response = requestWithRetry(request)
            ?: return Result(
                verdict = "UNAVAILABLE",
                summary = "Web analysis unavailable right now.",
                sources = emptyList(),
                error = "Linkup failed after retries"
            )

        val answer = response.answer?.trim().orEmpty()
        val sources = response.sources
            .orEmpty()
            .mapNotNull { src ->
                val name = src.name?.trim().orEmpty()
                val url = src.url?.trim().orEmpty()
                if (name.isBlank() || !url.startsWith("http")) null else AiSource(name, url)
            }
            .distinctBy { it.url }

        val verdict = classifyVerdict(answer)
        val polishedSummary = enforceFiveSentences(answer)
        val fallbackSource = buildFallbackSource(mainClaim)
        val outputSources = if (sources.isNotEmpty()) sources else listOf(fallbackSource)

        withContext(Dispatchers.Main.immediate) { onProgress(OverlayProgressStep.DONE) }
        Log.d(tag, "Final verdict: $verdict")
        return Result(
            verdict = verdict,
            summary = polishedSummary,
            sources = outputSources,
            claim = mainClaim
        )
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

    private suspend fun extractMainClaim(rawText: String): String {
        val prompt = """You are extracting the main claim from social media text.

TASK: Read the text below and extract ONLY the fact-checkable claim or statement.

IGNORE:
- Profile/page names (e.g., "Atty. Race Del Rosario", "@user123")
- UI elements (e.g., "Open story", "Profile picture", "More options", "Follow", "Close", "Search")
- Timestamps (e.g., "3h ago", "23 hours ago", "yesterday")
- Metadata (e.g., "Shared with: Public", "Followers")
- Navigation (e.g., "view story", "see more", "reply")
- Patterns like "X's post", "for X's post"

EXTRACT:
- The actual claim/statement being made
- News or events being reported
- Direct quotes from people
- Statistics, numbers, facts
- Claims about policies, people, events

TEXT:
$rawText

OUTPUT: Return ONLY the main claim as one paragraph. If no fact-checkable claim found, respond with exactly: NO_CLAIM_FOUND"""

        return claimExtractor.extract(prompt) {
            rawText.lines().filter { it.length > 30 && !it.contains("post") && !it.contains("profile") && !it.contains("ago") }.take(3).joinToString(" ").take(500)
        }
    }

    private fun buildSourcingQuery(claim: String): String {
        return buildString {
            append("Verify the following claim and provide a sourced answer.\n\n")
            append("Claim: $claim\n\n")
            append("Format:\n")
            append("Sentence 1: Verdict (TRUE/FALSE/MISLEADING/UNVERIFIED)\n")
            append("Sentence 2-3: Key evidence from credible sources\n")
            append("Sentence 4: Important context or caveats\n")
            append("Sentence 5: Final recommendation\n")
            append("Keep factual, cite sources where possible.")
        }.take(1200)
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

        val picked = when {
            split.size >= 5 -> split.take(5)
            split.size == 4 -> split + "Please verify using the cited source."
            split.size == 3 -> split + listOf(
                "Available evidence should be reviewed carefully.",
                "Please verify using the cited source."
            )
            split.size == 2 -> split + listOf(
                "Evidence appears limited from web search.",
                "Cross-check with trusted publications.",
                "Please verify using the cited source."
            )
            split.size == 1 -> listOf(
                split.first(),
                "Evidence from web search should be reviewed carefully.",
                "Some details may be missing.",
                "Cross-check with trusted publications.",
                "Please verify using the cited source."
            )
            else -> fallback
        }

        return picked.joinToString(" ")
    }

    private fun ensurePeriod(text: String): String {
        return if (text.endsWith('.') || text.endsWith('!') || text.endsWith('?')) {
            text
        } else {
            "$text."
        }
    }

    private fun buildFallbackSource(text: String): AiSource {
        val q = text.take(100).trim()
        val encoded = Uri.encode(q.ifBlank { "claim verification" })
        return AiSource(
            title = "Web Search",
            url = "https://www.google.com/search?q=$encoded"
        )
    }
}
