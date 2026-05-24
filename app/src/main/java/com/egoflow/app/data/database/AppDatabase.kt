package com.egoflow.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.egoflow.app.data.dao.*
import com.egoflow.app.data.entity.*

@Database(
    entities = [
        TaskEntity::class,
        EvolutionBacklogEntity::class,
        HardBlockEntity::class,
        DailyMetricsEntity::class,
        ChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun evolutionBacklogDao(): EvolutionBacklogDao
    abstract fun hardBlockDao(): HardBlockDao
    abstract fun dailyMetricsDao(): DailyMetricsDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE hard_blocks ADD COLUMN category TEXT NOT NULL DEFAULT 'MAIN_LINE'")
            db.execSQL("ALTER TABLE hard_blocks ADD COLUMN drain_level TEXT NOT NULL DEFAULT 'HIGH'")
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "egoflow_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
