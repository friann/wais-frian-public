package com.wais.network.linkup

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface LinkupApi {
    @POST("v1/search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Query("q") queryOverride: String? = null,
        @Body request: LinkupSearchRequest
    ): LinkupSearchResponse
}
