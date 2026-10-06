package com.wais.data.local

import com.wais.domain.model.AnalysisResult
import kotlinx.coroutines.flow.Flow

class FactCheckRepository(private val dao: FactCheckDao) {

    fun getAllFactChecks(): Flow<List<FactCheckEntity>> = dao.getAllFactChecks()

    fun getRecentFactChecks(limit: Int = 50): Flow<List<FactCheckEntity>> =
        dao.getRecentFactChecks(limit)

    fun getCount(): Flow<Int> = dao.getCount()

    suspend fun saveFromResult(
        result: AnalysisResult,
        sourceUrl: String,
        source: String = "overlay"
    ): Long {
        val entity = FactCheckEntity(
            sourceUrl = sourceUrl,
            claim = result.extractedText ?: sourceUrl,
            verdict = result.aiVerdict.ifEmpty { "ANALYZED" },
            summary = result.aiSummary ?: "No summary available",
            legitimacyPercent = result.legitimacyPercentage,
            sourceLinks = result.aiSources.map { it.url },
            errors = result.errors,
            source = source
        )
        return dao.insert(entity)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()
}
