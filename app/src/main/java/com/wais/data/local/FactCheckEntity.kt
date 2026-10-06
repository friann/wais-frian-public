package com.wais.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "fact_checks")
@TypeConverters(FactCheckConverters::class)
data class FactCheckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUrl: String,
    val claim: String,
    val verdict: String,
    val summary: String,
    val legitimacyPercent: Int?,
    val sourceLinks: List<String>,
    val errors: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "overlay"
)

class FactCheckConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
