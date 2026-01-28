package com.mgomanager.app.xposed

import android.content.Context
import java.io.File

/**
 * Provider for App Set ID and SSAID from MGO Manager.
 * Reads from a shared file in /data/local/tmp/ which is world-readable.
 *
 * File format: appSetId|ssaid|accountName|timestamp
 *
 * Also supports SSAID capture mode for backup fallback when settings_ssaid.xml is missing.
 */
object AppSetIdProvider {
    // Shared file written by MGO Manager after restore
    private const val SHARED_FILE_PATH = "/data/local/tmp/mgo_current_appsetid.txt"

    // Capture file - written by hook when in capture mode, read by MGO Manager during backup
    private const val CAPTURE_FILE_PATH = "/data/local/tmp/mgo_captured_ssaid.txt"

    // Capture mode flag file - if exists, hook writes original android_id to capture file
    private const val CAPTURE_MODE_FLAG = "/data/local/tmp/mgo_capture_mode"

    private const val CACHE_DURATION_MS = 5000L // 5 seconds cache

    private var cachedAppSetId: String? = null
    private var cachedSsaid: String? = null
    private var cachedAccountName: String? = null
    private var lastCacheTime: Long = 0

    /**
     * Check if capture mode is active (MGO Manager wants to capture the original Android ID).
     */
    fun isCaptureMode(): Boolean {
        return File(CAPTURE_MODE_FLAG).exists()
    }

    /**
     * Write captured Android ID to the capture file.
     * Called by hook when in capture mode.
     */
    fun writeCapturedSsaid(androidId: String) {
        try {
            val file = File(CAPTURE_FILE_PATH)
            file.writeText("$androidId|${System.currentTimeMillis()}")
            HookLogger.log("✓ Captured Android ID: $androidId")
        } catch (e: Exception) {
            HookLogger.logError("Failed to write captured SSAID", e)
        }
    }

    /**
     * Refresh cache by re-reading the shared file.
     */
    private fun refreshCache(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (cachedAppSetId != null && (currentTime - lastCacheTime) < CACHE_DURATION_MS) {
            return true
        }

        return try {
            val file = File(SHARED_FILE_PATH)

            if (!file.exists() || !file.canRead()) {
                HookLogger.log("Shared file not accessible: $SHARED_FILE_PATH")
                return false
            }

            val content = file.readText().trim()
            if (content.isEmpty()) {
                HookLogger.log("Shared file is empty")
                return false
            }

            // Parse format: appSetId|ssaid|accountName|timestamp
            val parts = content.split("|")
            if (parts.size < 3) {
                // Fallback for old format: appSetId|accountName|timestamp
                if (parts.size >= 2) {
                    cachedAppSetId = parts[0].takeIf { it.isNotBlank() && it != "nicht vorhanden" }
                    cachedSsaid = null
                    cachedAccountName = parts[1]
                    lastCacheTime = currentTime
                    HookLogger.log("✓ Loaded (old format) AppSetId: ${cachedAppSetId}")
                    return cachedAppSetId != null
                }
                HookLogger.log("Invalid shared file format: $content")
                return false
            }

            val appSetId = parts[0]
            val ssaid = parts[1]
            val accountName = parts[2]

            // Validate and cache
            cachedAppSetId = appSetId.takeIf { it.isNotBlank() && it != "nicht vorhanden" }
            cachedSsaid = ssaid.takeIf { it.isNotBlank() && it != "nicht vorhanden" }
            cachedAccountName = accountName
            lastCacheTime = currentTime

            HookLogger.log("✓ Loaded AppSetId: $cachedAppSetId, SSAID: $cachedSsaid (Account: $accountName)")
            true

        } catch (e: Exception) {
            HookLogger.logError("Failed to read shared file", e)
            false
        }
    }

    /**
     * Get the App Set ID of the last restored account.
     */
    fun getAppSetId(context: Context?): String? {
        refreshCache()
        return cachedAppSetId
    }

    /**
     * Get the SSAID (Android ID) of the last restored account.
     * This is returned for Settings.Secure.getString("android_id") calls.
     */
    fun getSsaid(context: Context?): String? {
        refreshCache()
        return cachedSsaid
    }

    /**
     * Invalidate the cache to force a fresh file read on next call.
     */
    fun invalidateCache() {
        cachedAppSetId = null
        cachedSsaid = null
        cachedAccountName = null
        lastCacheTime = 0
        HookLogger.log("Cache invalidated")
    }
}
