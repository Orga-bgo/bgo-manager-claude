package com.mgomanager.app.xposed

import android.content.Context
import java.io.File

/**
 * Provider for App Set ID and SSAID from MGO Manager.
 * Reads from a shared file in /data/local/tmp/ which is world-readable.
 *
 * Old file format: appSetId|ssaid|accountName|timestamp
 * New extended format: appSetId|ssaid|deviceName|gsfId|gaid|appSetIdDev|accountName|timestamp
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

    // Basic IDs
    private var cachedAppSetId: String? = null
    private var cachedSsaid: String? = null
    private var cachedAccountName: String? = null
    private var lastCacheTime: Long = 0

    // Extended IDs for "Create New Account" feature
    private var cachedDeviceName: String? = null
    private var cachedGsfId: String? = null
    private var cachedGaid: String? = null
    private var cachedAppSetIdDev: String? = null

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

            // Parse format based on number of parts
            val parts = content.split("|")
            when {
                // New extended format: appSetId|ssaid|deviceName|gsfId|gaid|appSetIdDev|accountName|timestamp
                parts.size >= 8 -> {
                    cachedAppSetId = parts[0].takeIf { it.isNotBlank() && it != "nicht vorhanden" }
                    cachedSsaid = parts[1].takeIf { it.isNotBlank() && it != "nicht vorhanden" }
                    cachedDeviceName = parts[2].takeIf { it.isNotBlank() }
                    cachedGsfId = parts[3].takeIf { it.isNotBlank() }
                    cachedGaid = parts[4].takeIf { it.isNotBlank() }
                    cachedAppSetIdDev = parts[5].takeIf { it.isNotBlank() }
                    cachedAccountName = parts[6]
                    lastCacheTime = currentTime
                    HookLogger.log("✓ Loaded extended IDs for: $cachedAccountName (AppSetId: $cachedAppSetId, SSAID: $cachedSsaid, DeviceName: $cachedDeviceName)")
                }
                // Old format: appSetId|ssaid|accountName|timestamp
                parts.size >= 3 -> {
                    cachedAppSetId = parts[0].takeIf { it.isNotBlank() && it != "nicht vorhanden" }
                    cachedSsaid = parts[1].takeIf { it.isNotBlank() && it != "nicht vorhanden" }
                    cachedAccountName = parts[2]
                    // Clear extended IDs for old format
                    cachedDeviceName = null
                    cachedGsfId = null
                    cachedGaid = null
                    cachedAppSetIdDev = null
                    lastCacheTime = currentTime
                    HookLogger.log("✓ Loaded AppSetId: $cachedAppSetId, SSAID: $cachedSsaid (Account: $cachedAccountName)")
                }
                // Very old format: appSetId|accountName|timestamp
                parts.size >= 2 -> {
                    cachedAppSetId = parts[0].takeIf { it.isNotBlank() && it != "nicht vorhanden" }
                    cachedSsaid = null
                    cachedAccountName = parts[1]
                    cachedDeviceName = null
                    cachedGsfId = null
                    cachedGaid = null
                    cachedAppSetIdDev = null
                    lastCacheTime = currentTime
                    HookLogger.log("✓ Loaded (legacy format) AppSetId: $cachedAppSetId")
                }
                else -> {
                    HookLogger.log("Invalid shared file format: $content")
                    return false
                }
            }
            cachedAppSetId != null

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
     * Get the Device Name for the current account.
     */
    fun getDeviceName(context: Context?): String? {
        refreshCache()
        return cachedDeviceName
    }

    /**
     * Get the GSF ID (Google Services Framework ID) for the current account.
     */
    fun getGsfId(context: Context?): String? {
        refreshCache()
        return cachedGsfId
    }

    /**
     * Get the GAID (Google Advertising ID) for the current account.
     */
    fun getGaid(context: Context?): String? {
        refreshCache()
        return cachedGaid
    }

    /**
     * Get the Developer App Set ID for the current account.
     */
    fun getAppSetIdDev(context: Context?): String? {
        refreshCache()
        return cachedAppSetIdDev
    }

    /**
     * Get the account name for the current account.
     */
    fun getAccountName(): String? {
        refreshCache()
        return cachedAccountName
    }

    /**
     * Invalidate the cache to force a fresh file read on next call.
     */
    fun invalidateCache() {
        cachedAppSetId = null
        cachedSsaid = null
        cachedAccountName = null
        cachedDeviceName = null
        cachedGsfId = null
        cachedGaid = null
        cachedAppSetIdDev = null
        lastCacheTime = 0
        HookLogger.log("Cache invalidated")
    }
}
