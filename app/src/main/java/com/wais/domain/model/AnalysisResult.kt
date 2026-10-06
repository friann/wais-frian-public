package com.wais.domain.model

data class AnalysisResult(
    val factVerdict: FactVerdict = FactVerdict.NOT_APPLICABLE,
    val factPublisher: String? = null,
    val factRating: String? = null,
    val factUrl: String? = null,
    val aiVerdict: String = "UNVERIFIED",
    val aiSummary: String? = null,
    val aiSources: List<AiSource> = emptyList(),
    val extractedText: String? = null,
    val errors: List<String> = emptyList(),
    val legitimacyPercentage: Int? = null
)

data class AiSource(
    val title: String,
    val url: String
)

enum class FactVerdict {
    SUPPORTED,
    REFUTED,
    MIXED,
    NOT_FOUND,
    NOT_APPLICABLE,
    ERROR
}
