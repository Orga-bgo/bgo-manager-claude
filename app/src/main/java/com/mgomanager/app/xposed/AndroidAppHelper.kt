package com.mgomanager.app.xposed

import android.content.Context

/**
 * Helper for accessing the current application context in Xposed hooks.
 * Uses reflection to get the context from ActivityThread.
 */
object AndroidAppHelper {

    /**
     * Returns the current application context, or null if not available.
     * This is called from within the hooked app's process (Monopoly GO),
     * so it returns Monopoly GO's context, NOT MGO Manager's context.
     *
     * May return null during early app initialization.
     */
    fun currentApplication(): Context? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
            currentApplicationMethod.invoke(null) as? Context
        } catch (e: Exception) {
            HookLogger.logError("Failed to get current application context", e)
            null
        }
    }
}
