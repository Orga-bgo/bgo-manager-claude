package com.mgomanager.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.dao.LogDao
import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.local.database.entities.LogEntity

/**
 * Main Room Database for MGO Manager
 */
@Database(
    entities = [AccountEntity::class, LogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun logDao(): LogDao

    companion object {
        const val DATABASE_NAME = "mgo_manager.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from version 1 to 2: Add isLastRestored column for Xposed hook support
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN isLastRestored INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Migration from version 2 to 3: Add extended IDs for "Create New Account" feature
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE accounts ADD COLUMN deviceName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE accounts ADD COLUMN androidId TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE accounts ADD COLUMN appSetIdApp TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE accounts ADD COLUMN appSetIdDev TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE accounts ADD COLUMN gsfId TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE accounts ADD COLUMN generatedGaid TEXT DEFAULT NULL")
            }
        }

        /**
         * Get singleton instance for Xposed hook access.
         * This bypasses Hilt DI for use in Xposed context where DI is not available.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Use applicationContext if available, otherwise use context directly
                // This handles early initialization where applicationContext may be null
                val appContext = context.applicationContext ?: context

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .allowMainThreadQueries() // Required for Xposed synchronous access
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
