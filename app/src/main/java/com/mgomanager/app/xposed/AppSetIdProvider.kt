package com.mgomanager.app.xposed

import android.content.Context
import java.io.File

/**
 * Provider for the App Set ID from MGO Manager.
 * Reads from a shared file in /data/local/tmp/ which is world-readable.
 *
 * This approach avoids SQLite WAL mode issues that prevent cross-process
 * database access without write permissions.
 */
object AppSetIdProvider {
    // Shared file written by MGO Manager after restore
    private const val SHARED_FILE_PATH = "/data/local/tmp/mgo_current_appsetid.txt"
    private const val CACHE_DURATION_MS = 5000L // 5 seconds cache

    private var cachedAppSetId: String? = null
    private var cachedAccountName: String? = null
    private var lastCacheTime: Long = 0

    /**
     * Get the App Set ID of the last restored account from shared file.
     *
     * File format: appSetId|accountName|timestamp
     *
     * @param context The current application context (unused but kept for API compatibility)
     * @return The App Set ID string or null if no account was restored or file not accessible
     */
    fun getAppSetId(context: Context?): String? {
        // Check cache first
        val currentTime = System.currentTimeMillis()
        if (cachedAppSetId != null && (currentTime - lastCacheTime) < CACHE_DURATION_MS) {
            return cachedAppSetId
        }

        return try {
            val file = File(SHARED_FILE_PATH)

            // Check if file exists and is readable
            if (!file.exists()) {
                HookLogger.log("Shared file not found: $SHARED_FILE_PATH")
                return null
            }

            if (!file.canRead()) {
                HookLogger.log("Shared file not readable: $SHARED_FILE_PATH")
                return null
            }

            // Read and parse file content
            val content = file.readText().trim()
            if (content.isEmpty()) {
                HookLogger.log("Shared file is empty")
                return null
            }

            // Parse format: appSetId|accountName|timestamp
            val parts = content.split("|")
            if (parts.size < 2) {
                HookLogger.log("Invalid shared file format: $content")
                return null
            }

            val appSetId = parts[0]
            val accountName = parts[1]

            // Validate App Set ID format (UUID-like)
            if (appSetId.isBlank() || appSetId == "nicht vorhanden") {
                HookLogger.log("Invalid App Set ID in shared file: $appSetId")
                return null
            }

            // Update cache
            cachedAppSetId = appSetId
            cachedAccountName = accountName
            lastCacheTime = currentTime

            HookLogger.log("✓ Loaded App Set ID: $appSetId (Account: $accountName)")
            appSetId

        } catch (e: Exception) {
            HookLogger.logError("Failed to read App Set ID from shared file", e)
            null
        }
    }

    /**
     * Invalidate the cache to force a fresh file read on next call.
     */
    fun invalidateCache() {
        cachedAppSetId = null
        cachedAccountName = null
        lastCacheTime = 0
        HookLogger.log("App Set ID cache invalidated")
    }
}
