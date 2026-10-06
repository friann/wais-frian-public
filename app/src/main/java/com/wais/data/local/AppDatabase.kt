package com.wais.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FactCheckEntity::class], version = 1, exportSchema = false)
@TypeConverters(FactCheckConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun factCheckDao(): FactCheckDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wais_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
