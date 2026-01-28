package com.mgomanager.app.xposed

import de.robv.android.xposed.XposedBridge

/**
 * Logger utility for Xposed hook operations.
 * Uses XposedBridge.log() instead of Android Logcat for visibility in LSPosed Manager.
 */
object HookLogger {
    private const val TAG = "MGO_Hook"

    /**
     * Log an info-level message.
     */
    fun log(message: String) {
        XposedBridge.log("[$TAG] $message")
    }

    /**
     * Log an error-level message with optional exception.
     */
    fun logError(message: String, throwable: Throwable? = null) {
        XposedBridge.log("[$TAG] ERROR: $message")
        throwable?.let {
            XposedBridge.log("[$TAG] Exception: ${it.message}")
            XposedBridge.log(it)
        }
    }
}
