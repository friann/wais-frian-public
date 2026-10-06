package com.wais.network

import com.google.gson.GsonBuilder
import com.wais.data.ClaimExtractor
import com.wais.network.gemini.GeminiApi
import com.wais.network.jina.JinaApi
import com.wais.network.linkup.LinkupApi
import com.wais.network.openrouter.OpenRouterApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClients {
    private fun baseOkHttp(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun linkupApi(): LinkupApi {
        return Retrofit.Builder()
            .baseUrl("https://api.linkup.so/")
            .client(baseOkHttp())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LinkupApi::class.java)
    }

    fun openRouterApi(): OpenRouterApi {
        return Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/")
            .client(baseOkHttp())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenRouterApi::class.java)
    }

    fun geminiApi(): GeminiApi {
        // Longer read timeout: image requests can take more than 30s.
        val client = baseOkHttp().newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }

    fun claimExtractor(): ClaimExtractor = ClaimExtractor(geminiApi(), openRouterApi())

    fun jinaApi(): JinaApi {
        val lenientGson = GsonBuilder()
            .setLenient()
            .create()
        
        return Retrofit.Builder()
            .baseUrl("https://r.jina.ai")
            .client(baseOkHttp())
            .addConverterFactory(GsonConverterFactory.create(lenientGson))
            .build()
            .create(JinaApi::class.java)
    }
}
