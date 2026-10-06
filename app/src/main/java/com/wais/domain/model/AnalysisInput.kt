package com.wais.domain.model

import android.net.Uri

data class AnalysisInput(
    val text: String? = null,
    val imageUri: Uri? = null,
    val url: String? = null,
    val source: Source
) {
    enum class Source {
        OVERLAY_CAPTURE,
        SHARE_TEXT,
        SHARE_IMAGE
    }
}
