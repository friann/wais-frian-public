package com.wais.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FactCheckDao {
    @Query("SELECT * FROM fact_checks ORDER BY timestamp DESC")
    fun getAllFactChecks(): Flow<List<FactCheckEntity>>

    @Query("SELECT * FROM fact_checks ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFactChecks(limit: Int): Flow<List<FactCheckEntity>>

    @Query("SELECT COUNT(*) FROM fact_checks")
    fun getCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(factCheck: FactCheckEntity): Long

    @Query("DELETE FROM fact_checks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM fact_checks")
    suspend fun deleteAll()
}
