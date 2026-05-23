package com.egoflow.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.egoflow.app.data.dao.*
import com.egoflow.app.data.entity.*

@Database(
    entities = [
        TaskEntity::class,
        EvolutionBacklogEntity::class,
        HardBlockEntity::class,
        DailyMetricsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun evolutionBacklogDao(): EvolutionBacklogDao
    abstract fun hardBlockDao(): HardBlockDao
    abstract fun dailyMetricsDao(): DailyMetricsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "egoflow_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
