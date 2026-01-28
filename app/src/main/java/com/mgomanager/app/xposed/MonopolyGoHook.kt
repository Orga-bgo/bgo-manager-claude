package com.mgomanager.app.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Main Xposed hook class for Monopoly GO.
 * Intercepts App Set ID requests and returns the ID from the last restored account.
 *
 * Note: SSAID (Android ID) is now handled by directly modifying settings_ssaid.xml
 * via SsaidManager during restore. This is more reliable than hooking Settings.Secure.
 *
 * Entry point defined in: assets/xposed_init
 */
class MonopolyGoHook : IXposedHookLoadPackage {

    companion object {
        private const val TARGET_PACKAGE = "com.scopely.monopolygo"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // Only hook Monopoly GO
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        HookLogger.log("Hooking Monopoly GO (${lpparam.packageName})")

        try {
            // Only hook App Set ID - SSAID is handled via settings_ssaid.xml modification
            hookAppSetIdClient(lpparam)
            HookLogger.log("App Set ID hook installed successfully")
        } catch (e: Exception) {
            HookLogger.logError("Failed to install hooks", e)
        }
    }

    // Note: hookSettingsSecure() has been removed.
    // SSAID is now handled by directly modifying settings_ssaid.xml via SsaidManager
    // during restore. This is more reliable and doesn't require the Xposed module to be active.

    /**
     * Hook AppSetIdClient.getAppSetIdInfo() to return our custom App Set ID.
     * This is the primary method Google Play Services uses for App Set ID.
     *
     * Uses multiple strategies to find the implementation class since obfuscated
     * class names vary across GMS versions.
     */
    private fun hookAppSetIdClient(lpparam: LoadPackageParam) {
        var hooked = false

        // Strategy 1: Try known implementation class names (obfuscated names vary by GMS version)
        val implClassNames = listOf(
            // Common obfuscated names
            "com.google.android.gms.appset.internal.zzc",
            "com.google.android.gms.appset.internal.zzd",
            "com.google.android.gms.appset.internal.zze",
            "com.google.android.gms.appset.internal.zzf",
            "com.google.android.gms.appset.internal.zza",
            "com.google.android.gms.appset.internal.zzb",
            // Non-obfuscated names
            "com.google.android.gms.appset.AppSetIdClientImpl",
            "com.google.android.gms.appset.internal.AppSetIdClientImpl"
        )

        for (className in implClassNames) {
            if (hooked) break
            hooked = tryHookClass(className, lpparam)
        }

        // Strategy 2: Try to hook the Task result listener instead
        // When getAppSetIdInfo() returns a Task, we can hook the Task's result
        if (!hooked) {
            hooked = tryHookTaskResult(lpparam)
        }

        if (hooked) {
            HookLogger.log("AppSetIdClient hook installed successfully")
        } else {
            HookLogger.log("AppSetIdClient implementation not found - using Settings.Secure hook only")
        }
    }

    /**
     * Try to hook a specific class for getAppSetIdInfo method.
     */
    private fun tryHookClass(className: String, lpparam: LoadPackageParam): Boolean {
        return try {
            val implClass = XposedHelpers.findClass(className, lpparam.classLoader)

            val hooks = XposedBridge.hookAllMethods(
                implClass,
                "getAppSetIdInfo",
                createAppSetIdHookCallback(lpparam)
            )

            if (hooks.isNotEmpty()) {
                HookLogger.log("AppSetIdClient hooked via $className (${hooks.size} methods)")
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            // Class not found, try next
            false
        }
    }

    /**
     * Alternative strategy: Hook the Task.addOnSuccessListener to intercept results.
     * This works regardless of which class implements AppSetIdClient.
     */
    private fun tryHookTaskResult(lpparam: LoadPackageParam): Boolean {
        return try {
            // Find AppSetIdInfo class to identify relevant Tasks
            val appSetIdInfoClass = XposedHelpers.findClass(
                "com.google.android.gms.appset.AppSetIdInfo",
                lpparam.classLoader
            )

            // Hook AppSetIdInfo.getId() to return our ID
            val hooks = XposedBridge.hookAllMethods(
                appSetIdInfoClass,
                "getId",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = AndroidAppHelper.currentApplication()
                        val appSetId = AppSetIdProvider.getAppSetId(context)

                        if (appSetId != null && appSetId != "nicht vorhanden") {
                            val original = param.result as? String
                            param.result = appSetId
                            HookLogger.log("Hooked AppSetIdInfo.getId(): Original=$original, Replaced=$appSetId")
                        }
                    }
                }
            )

            if (hooks.isNotEmpty()) {
                HookLogger.log("AppSetIdInfo.getId hooked as fallback (${hooks.size} methods)")
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            HookLogger.log("Failed to hook AppSetIdInfo.getId: ${e.message}")
            false
        }
    }

    /**
     * Creates the hook callback for AppSetIdClient.getAppSetIdInfo().
     */
    private fun createAppSetIdHookCallback(lpparam: LoadPackageParam): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = AndroidAppHelper.currentApplication()
                if (context == null) {
                    HookLogger.log("Context not available yet for AppSetIdClient hook")
                    return
                }

                val appSetId = AppSetIdProvider.getAppSetId(context)

                if (appSetId != null && appSetId != "nicht vorhanden") {
                    try {
                        // Create a fake AppSetIdInfo with our custom ID
                        val fakeAppSetIdInfo = createFakeAppSetIdInfo(appSetId, lpparam)

                        if (fakeAppSetIdInfo != null) {
                            // Wrap in a completed Task
                            val completedTask = createCompletedTask(fakeAppSetIdInfo, lpparam)
                            if (completedTask != null) {
                                param.result = completedTask
                                HookLogger.log("Hooked AppSetIdClient.getAppSetIdInfo: Replaced with $appSetId")
                            }
                        }
                    } catch (e: Exception) {
                        HookLogger.logError("Failed to create fake AppSetIdInfo", e)
                    }
                }
            }
        }
    }

    /**
     * Creates a fake AppSetIdInfo object with the given App Set ID.
     */
    private fun createFakeAppSetIdInfo(appSetId: String, lpparam: LoadPackageParam): Any? {
        return try {
            val appSetIdInfoClass = XposedHelpers.findClass(
                "com.google.android.gms.appset.AppSetIdInfo",
                lpparam.classLoader
            )

            // AppSetIdInfo constructor: AppSetIdInfo(String id, int scope)
            // Scope 1 = SCOPE_APP (per-app ID)
            val constructor = appSetIdInfoClass.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType)
            constructor.isAccessible = true
            constructor.newInstance(appSetId, 1)
        } catch (e: Exception) {
            HookLogger.logError("Failed to instantiate AppSetIdInfo", e)
            null
        }
    }

    /**
     * Creates a completed Task<AppSetIdInfo> wrapping the given result.
     */
    private fun createCompletedTask(result: Any, lpparam: LoadPackageParam): Any? {
        return try {
            val tasksClass = XposedHelpers.findClass(
                "com.google.android.gms.tasks.Tasks",
                lpparam.classLoader
            )

            // Tasks.forResult(T result) returns a completed Task<T>
            val forResultMethod = tasksClass.getMethod("forResult", Any::class.java)
            forResultMethod.invoke(null, result)
        } catch (e: Exception) {
            HookLogger.logError("Failed to create completed Task", e)
            null
        }
    }
}
