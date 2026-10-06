package com.wais.network.linkup

data class LinkupSearchRequest(
    val q: String,
    val depth: String = "standard",
    val outputType: String = "sourcedAnswer",
    val includeImages: Boolean = false
)

data class LinkupSearchResponse(
    val answer: String? = null,
    val sources: List<LinkupSourceDto>? = null
)

data class LinkupSourceDto(
    val name: String? = null,
    val url: String? = null,
    val snippet: String? = null
)
