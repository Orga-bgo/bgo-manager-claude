package com.mgomanager.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mgomanager.app.data.local.database.dao.AccountDao
import com.mgomanager.app.data.local.database.dao.LogDao
import com.mgomanager.app.data.local.database.entities.AccountEntity
import com.mgomanager.app.data.local.database.entities.LogEntity

/**
 * Main Room Database for MGO Manager
 */
@Database(
    entities = [AccountEntity::class, LogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun logDao(): LogDao

    companion object {
        const val DATABASE_NAME = "mgo_manager.db"
    }
}
