package com.mgomanager.app.xposed

import android.content.Context
import com.mgomanager.app.data.local.database.AppDatabase

/**
 * Provider for the App Set ID from MGO Manager database.
 * Reads directly from the Room database without SharedPreferences or Broadcasts.
 */
object AppSetIdProvider {
    private const val MGO_MANAGER_PACKAGE = "com.mgomanager.app"
    private const val CACHE_DURATION_MS = 5000L // 5 seconds cache

    private var cachedAppSetId: String? = null
    private var lastCacheTime: Long = 0

    /**
     * Get the App Set ID of the last restored account from MGO Manager database.
     *
     * @param context The current application context (from Monopoly GO process)
     * @return The App Set ID string or null if no account was restored or context unavailable
     */
    fun getAppSetId(context: Context?): String? {
        if (context == null) {
            HookLogger.log("Context is null, cannot read App Set ID")
            return null
        }

        // Check cache first
        val currentTime = System.currentTimeMillis()
        if (cachedAppSetId != null && (currentTime - lastCacheTime) < CACHE_DURATION_MS) {
            HookLogger.log("Returning cached App Set ID: $cachedAppSetId")
            return cachedAppSetId
        }

        return try {
            // Create MGO Manager context to access its database
            val mgoContext = context.createPackageContext(
                MGO_MANAGER_PACKAGE,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )

            // Get database instance (use mgoContext directly, not applicationContext)
            val database = AppDatabase.getInstance(mgoContext)

            // Query for last restored account (synchronous call)
            val account = database.accountDao().getLastRestoredAccountSync()

            if (account != null) {
                cachedAppSetId = account.appSetId
                lastCacheTime = currentTime
                HookLogger.log("Read App Set ID from database: ${account.appSetId} (Account: ${account.accountName})")
                account.appSetId
            } else {
                HookLogger.log("No restored account found in database")
                cachedAppSetId = null
                null
            }
        } catch (e: Exception) {
            HookLogger.logError("Failed to read App Set ID from database", e)
            null
        }
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
