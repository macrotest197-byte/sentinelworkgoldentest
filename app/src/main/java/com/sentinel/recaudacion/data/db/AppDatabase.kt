package com.sentinel.recaudacion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BlacklistEntity::class, OutboxEntity::class, IncidentEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blacklistDao(): BlacklistDao
    abstract fun outboxDao(): OutboxDao
    abstract fun incidentDao(): IncidentDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incidents ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blacklist ADD COLUMN personId TEXT")
                db.execSQL("ALTER TABLE blacklist ADD COLUMN primaryName TEXT")
                db.execSQL("ALTER TABLE blacklist ADD COLUMN knownNameVariants TEXT")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telegram_outbox ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incidents ADD COLUMN personId TEXT")
                db.execSQL("ALTER TABLE incidents ADD COLUMN personName TEXT")
                db.execSQL("ALTER TABLE incidents ADD COLUMN confidence REAL")
                db.execSQL("ALTER TABLE incidents ADD COLUMN sourceExcerpt TEXT")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sentinel.db"
                )
                    // Durante el desarrollo recreamos el esquema si cambia.
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
