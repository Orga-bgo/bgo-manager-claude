package com.mgomanager.app.xposed

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Provider for the App Set ID from MGO Manager database.
 * Uses direct SQLite access to read from MGO Manager's database file.
 *
 * Note: This requires the database file to be readable, which is ensured
 * by setting appropriate permissions after database creation.
 */
object AppSetIdProvider {
    private const val MGO_MANAGER_PACKAGE = "com.mgomanager.app"
    private const val DATABASE_NAME = "mgo_manager.db"
    private const val CACHE_DURATION_MS = 5000L // 5 seconds cache

    // Possible database paths (varies by Android version and user)
    private val DATABASE_PATHS = listOf(
        "/data/data/$MGO_MANAGER_PACKAGE/databases/$DATABASE_NAME",
        "/data/user/0/$MGO_MANAGER_PACKAGE/databases/$DATABASE_NAME"
    )

    private var cachedAppSetId: String? = null
    private var lastCacheTime: Long = 0

    /**
     * Get the App Set ID of the last restored account from MGO Manager database.
     * Uses direct SQLite access to bypass Android's cross-app security restrictions.
     *
     * @param context The current application context (unused but kept for API compatibility)
     * @return The App Set ID string or null if no account was restored or database not accessible
     */
    fun getAppSetId(context: Context?): String? {
        // Check cache first
        val currentTime = System.currentTimeMillis()
        if (cachedAppSetId != null && (currentTime - lastCacheTime) < CACHE_DURATION_MS) {
            return cachedAppSetId
        }

        return try {
            // Find the database file
            val dbPath = findDatabasePath()
            if (dbPath == null) {
                HookLogger.log("MGO Manager database not found")
                return null
            }

            // Open database directly with read-only flag
            val db = SQLiteDatabase.openDatabase(
                dbPath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            try {
                // Query for last restored account
                val cursor = db.rawQuery(
                    "SELECT appSetId, accountName FROM accounts WHERE isLastRestored = 1 LIMIT 1",
                    null
                )

                cursor.use {
                    if (it.moveToFirst()) {
                        val appSetId = it.getString(0)
                        val accountName = it.getString(1)
                        cachedAppSetId = appSetId
                        lastCacheTime = currentTime
                        HookLogger.log("Read App Set ID from database: $appSetId (Account: $accountName)")
                        appSetId
                    } else {
                        HookLogger.log("No restored account found in database")
                        cachedAppSetId = null
                        null
                    }
                }
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            HookLogger.logError("Failed to read App Set ID from database", e)
            null
        }
    }

    /**
     * Find the MGO Manager database file path.
     * Checks multiple possible locations.
     */
    private fun findDatabasePath(): String? {
        for (path in DATABASE_PATHS) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return path
            }
        }
        // Log which paths were checked
        HookLogger.log("Database not found at: ${DATABASE_PATHS.joinToString(", ")}")
        return null
    }

    /**
     * Invalidate the cache to force a fresh database read on next call.
     */
    fun invalidateCache() {
        cachedAppSetId = null
        lastCacheTime = 0
        HookLogger.log("App Set ID cache invalidated")
    }
}
