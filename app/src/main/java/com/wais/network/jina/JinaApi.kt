package com.wais.network.jina

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface JinaApi {
    @GET
    suspend fun fetchContent(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Engine") engine: String = "browser"
    ): JinaResponse
}

data class JinaResponse(
    @SerializedName("code")
    val code: Int?,
    @SerializedName("status")
    val status: Int?,
    @SerializedName("data")
    val data: JinaData?,
    val text: String?
)

data class JinaData(
    @SerializedName("title")
    val title: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("url")
    val url: String?,
    @SerializedName("content")
    val content: String?,
    @SerializedName("publishedTime")
    val publishedTime: String?,
    @SerializedName("warning")
    val warning: String?
)
