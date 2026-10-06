package com.wais

import android.content.Context
import com.wais.data.LinkupRepository
import com.wais.data.ShareRepository
import com.wais.data.local.AppDatabase
import com.wais.data.local.FactCheckRepository
import com.wais.domain.AnalyzeContentUseCase
import com.wais.network.ApiClients

object AppGraph {
    fun provideFactCheckRepository(context: Context): FactCheckRepository {
        return FactCheckRepository(AppDatabase.getInstance(context).factCheckDao())
    }

    fun provideAnalyzeContentUseCase(@Suppress("UNUSED_PARAMETER") context: Context): AnalyzeContentUseCase {
        val linkup = LinkupRepository(
            linkupApi = ApiClients.linkupApi(),
            claimExtractor = ApiClients.claimExtractor()
        )
        return AnalyzeContentUseCase(linkup)
    }

    fun provideShareRepository(): ShareRepository {
        return ShareRepository(
            jinaApi = ApiClients.jinaApi(),
            claimExtractor = ApiClients.claimExtractor(),
            linkupApi = ApiClients.linkupApi()
        )
    }

    fun provideLinkupRepository(): LinkupRepository {
        return LinkupRepository(
            linkupApi = ApiClients.linkupApi(),
            claimExtractor = ApiClients.claimExtractor()
        )
    }
}
