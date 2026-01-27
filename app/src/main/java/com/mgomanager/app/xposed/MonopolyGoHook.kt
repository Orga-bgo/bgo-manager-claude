package com.mgomanager.app.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Main Xposed hook class for Monopoly GO.
 * Intercepts App Set ID and Android ID requests and returns the ID from the last restored account.
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
            hookSettingsSecure(lpparam)
            hookAppSetIdClient(lpparam)
            HookLogger.log("All hooks installed successfully")
        } catch (e: Exception) {
            HookLogger.logError("Failed to install hooks", e)
        }
    }

    /**
     * Hook Settings.Secure.getString() to replace android_id with our App Set ID.
     * Some apps use this as a fallback or alternative identifier.
     */
    private fun hookSettingsSecure(lpparam: LoadPackageParam) {
        try {
            val settingsSecureClass = XposedHelpers.findClass(
                "android.provider.Settings\$Secure",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                settingsSecureClass,
                "getString",
                android.content.ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val name = param.args[1] as? String

                        // Only intercept android_id requests
                        if (name == "android_id") {
                            val context = AndroidAppHelper.currentApplication()
                            if (context == null) {
                                HookLogger.log("Context not available yet, skipping hook")
                                return
                            }

                            val appSetId = AppSetIdProvider.getAppSetId(context)

                            if (appSetId != null && appSetId != "nicht vorhanden") {
                                val original = param.result as? String
                                param.result = appSetId
                                HookLogger.log("Hooked Settings.Secure.getString(android_id): Original=$original, Replaced=$appSetId")
                            }
                        }
                    }
                }
            )

            HookLogger.log("Settings.Secure.getString hooked")
        } catch (e: Exception) {
            HookLogger.logError("Failed to hook Settings.Secure", e)
        }
    }

    /**
     * Hook AppSetIdClient.getAppSetIdInfo() to return our custom App Set ID.
     * This is the primary method Google Play Services uses for App Set ID.
     *
     * Note: AppSetIdClient is an interface, so we need to hook all implementations
     * using XposedBridge.hookAllMethods.
     */
    private fun hookAppSetIdClient(lpparam: LoadPackageParam) {
        try {
            // Try to find the implementation class (varies by GMS version)
            val implClassNames = listOf(
                "com.google.android.gms.appset.internal.zzc",
                "com.google.android.gms.appset.internal.zzd",
                "com.google.android.gms.appset.AppSetIdClientImpl"
            )

            var hooked = false
            for (className in implClassNames) {
                try {
                    val implClass = XposedHelpers.findClass(className, lpparam.classLoader)

                    val hooks = XposedBridge.hookAllMethods(
                        implClass,
                        "getAppSetIdInfo",
                        object : XC_MethodHook() {
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
                                            param.result = completedTask
                                            HookLogger.log("Hooked AppSetIdClient.getAppSetIdInfo: Replaced with $appSetId")
                                        }
                                    } catch (e: Exception) {
                                        HookLogger.logError("Failed to create fake AppSetIdInfo", e)
                                    }
                                }
                            }
                        }
                    )

                    if (hooks.isNotEmpty()) {
                        HookLogger.log("AppSetIdClient implementation hooked via $className (${hooks.size} methods)")
                        hooked = true
                        break
                    }
                } catch (e: Throwable) {
                    // Class not found, try next
                }
            }

            if (!hooked) {
                HookLogger.log("AppSetIdClient implementation not found - App Set ID hook not available")
            }
        } catch (e: Throwable) {
            HookLogger.log("Failed to hook AppSetIdClient: ${e.message}")
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
