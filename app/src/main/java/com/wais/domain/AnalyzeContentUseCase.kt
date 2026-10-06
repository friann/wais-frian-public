package com.wais.domain

import android.util.Log
import com.wais.data.LinkupRepository
import com.wais.domain.model.AnalysisInput
import com.wais.domain.model.AnalysisResult
import com.wais.domain.model.FactVerdict

class AnalyzeContentUseCase(
    private val linkupRepository: LinkupRepository
) {
    private val tag = "AnalyzeUseCase"

    suspend operator fun invoke(input: AnalysisInput): AnalysisResult {
        val textPayload = input.text?.takeIf { it.isNotBlank() }
        val query = when {
            !textPayload.isNullOrBlank() -> textPayload
            !input.url.isNullOrBlank() -> input.url
            else -> null
        }
        Log.d(tag, "invoke() source=${input.source} hasText=${!query.isNullOrBlank()} hasImage=${input.imageUri != null} hasUrl=${!input.url.isNullOrBlank()}")

        val linkup = try {
            if (query == null) LinkupRepository.Result("UNVERIFIED", "No text to analyze.", emptyList())
            else linkupRepository.analyze(query)
        } catch (t: Throwable) {
            Log.e(tag, "Linkup failure", t)
            LinkupRepository.Result(
                verdict = "UNAVAILABLE",
                summary = "Web analysis unavailable.",
                sources = emptyList(),
                error = t.message ?: "Linkup failure"
            )
        }

        val errors = mutableListOf<String>().apply {
            linkup.error?.let { add(it) }
        }

        val verdictToPercentage = { verdict: String ->
            when (verdict.uppercase().trim()) {
                "TRUE", "SUPPORTED", "VERIFIED", "ACCURATE" -> 95
                "FALSE", "REFUTED", "MIXED", "UNVERIFIED" -> 20
                "PARTIALLY TRUE", "MOSTLY TRUE" -> 70
                "PARTIALLY FALSE", "MOSTLY FALSE" -> 40
                "UNAVAILABLE", "ERROR" -> null
                else -> null
            }
        }

        val result = AnalysisResult(
            factVerdict = FactVerdict.NOT_APPLICABLE,
            factPublisher = null,
            factRating = null,
            factUrl = null,
            aiVerdict = linkup.verdict,
            aiSummary = linkup.summary,
            aiSources = linkup.sources,
            extractedText = query,
            errors = errors,
            legitimacyPercentage = verdictToPercentage(linkup.verdict)
        )

        Log.d(tag, "final verdict=${result.factVerdict} publisher=${result.factPublisher} errors=${result.errors.size}")
        return result
    }
}
