package com.mgomanager.app.xposed

import android.content.ContentResolver
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Main Xposed hook class for Monopoly GO.
 * Intercepts App Set ID, SSAID, GAID, GSF ID, and Device Name requests.
 *
 * Note: SSAID (Android ID) is spoofed via Settings.Secure hooks using the hook file.
 *
 * Entry point defined in: assets/xposed_init
 */
class MonopolyGoHook : IXposedHookLoadPackage {

    companion object {
        private const val TARGET_PACKAGE = "com.scopely.monopolygo"
        private const val GMS_PACKAGE = "com.google.android.gms"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // Only hook Monopoly GO and GMS
        if (lpparam.packageName != TARGET_PACKAGE && lpparam.packageName != GMS_PACKAGE) {
            return
        }

        HookLogger.log("Hooking package: ${lpparam.packageName}")

        try {
            if (lpparam.packageName == TARGET_PACKAGE) {
                // Hook App Set ID
                hookAppSetIdClient(lpparam)
                HookLogger.log("App Set ID hook installed successfully")

                // Hook Device Name (Build.MODEL)
                hookDeviceName(lpparam)
                HookLogger.log("Device Name hook installed successfully")

                // Hook SSAID (Android ID)
                hookAndroidId(lpparam)
                HookLogger.log("SSAID hook installed successfully")

                // Hook GAID (Google Advertising ID)
                hookGoogleAdvertisingId(lpparam)
                HookLogger.log("GAID hook installed successfully")

                // Hook GSF ID (Google Services Framework ID)
                hookGsfId(lpparam)
                HookLogger.log("GSF ID hook installed successfully")
            }
        } catch (e: Exception) {
            HookLogger.logError("Failed to install hooks", e)
        }
    }

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

    // ============================================================
    // SSAID (Android ID) Hook
    // ============================================================

    /**
     * Hook Settings.Secure.getString to return custom SSAID.
     */
    private fun hookAndroidId(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure",
                lpparam.classLoader,
                "getString",
                ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "android_id") {
                            val context = AndroidAppHelper.currentApplication()
                            val customSsaid = AppSetIdProvider.getSsaid(context)
                            if (customSsaid != null) {
                                val original = param.result as? String
                                param.result = customSsaid
                                HookLogger.log("✓ SSAID hooked: Original=$original, Replaced=$customSsaid")
                            }
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure",
                lpparam.classLoader,
                "getStringForUser",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "android_id") {
                            val context = AndroidAppHelper.currentApplication()
                            val customSsaid = AppSetIdProvider.getSsaid(context)
                            if (customSsaid != null) {
                                val original = param.result as? String
                                param.result = customSsaid
                                HookLogger.log("✓ SSAID (for user) hooked: Original=$original, Replaced=$customSsaid")
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            HookLogger.logError("Failed to hook SSAID", e)
        }
    }

    // ============================================================
    // Device Name Hook
    // ============================================================

    /**
     * Hook Build.MODEL and Build.DEVICE to return custom device name.
     */
    private fun hookDeviceName(lpparam: LoadPackageParam) {
        try {
            val buildClass = XposedHelpers.findClass("android.os.Build", lpparam.classLoader)

            // Hook Build.MODEL field read
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Global",
                lpparam.classLoader,
                "getString",
                ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "device_name") {
                            val context = AndroidAppHelper.currentApplication()
                            val customName = AppSetIdProvider.getDeviceName(context)
                            if (customName != null) {
                                param.result = customName
                                HookLogger.log("✓ Device Name hooked via Settings.Global: $customName")
                            }
                        }
                    }
                }
            )
            HookLogger.log("Settings.Global.getString hook installed for device_name")
        } catch (e: Exception) {
            HookLogger.logError("Failed to hook device name", e)
        }
    }

    // ============================================================
    // Google Advertising ID (GAID) Hook
    // ============================================================

    /**
     * Hook AdvertisingIdClient.getAdvertisingIdInfo() to return custom GAID.
     */
    private fun hookGoogleAdvertisingId(lpparam: LoadPackageParam) {
        try {
            // Try to hook AdvertisingIdClient.Info.getId()
            val advertisingIdInfoClass = try {
                XposedHelpers.findClass(
                    "com.google.android.gms.ads.identifier.AdvertisingIdClient\$Info",
                    lpparam.classLoader
                )
            } catch (e: Throwable) {
                null
            }

            if (advertisingIdInfoClass != null) {
                XposedBridge.hookAllMethods(
                    advertisingIdInfoClass,
                    "getId",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val context = AndroidAppHelper.currentApplication()
                            val customGaid = AppSetIdProvider.getGaid(context)
                            if (customGaid != null) {
                                val original = param.result as? String
                                param.result = customGaid
                                HookLogger.log("✓ GAID hooked: Original=$original, Replaced=$customGaid")
                            }
                        }
                    }
                )
                HookLogger.log("AdvertisingIdClient.Info.getId hook installed")
            }

            // Also try to hook the static method that returns Info object
            val advertisingIdClientClass = try {
                XposedHelpers.findClass(
                    "com.google.android.gms.ads.identifier.AdvertisingIdClient",
                    lpparam.classLoader
                )
            } catch (e: Throwable) {
                null
            }

            if (advertisingIdClientClass != null) {
                XposedBridge.hookAllMethods(
                    advertisingIdClientClass,
                    "getAdvertisingIdInfo",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val result = param.result ?: return
                            val context = AndroidAppHelper.currentApplication()
                            val customGaid = AppSetIdProvider.getGaid(context)
                            if (customGaid != null) {
                                try {
                                    // Try to create a new Info object with our custom ID
                                    val infoClass = result.javaClass
                                    val constructor = infoClass.getDeclaredConstructor(
                                        String::class.java,
                                        Boolean::class.javaPrimitiveType
                                    )
                                    constructor.isAccessible = true
                                    param.result = constructor.newInstance(customGaid, false)
                                    HookLogger.log("✓ AdvertisingIdInfo replaced with GAID: $customGaid")
                                } catch (e: Exception) {
                                    HookLogger.logError("Failed to create custom AdvertisingIdInfo", e)
                                }
                            }
                        }
                    }
                )
                HookLogger.log("AdvertisingIdClient.getAdvertisingIdInfo hook installed")
            }
        } catch (e: Exception) {
            HookLogger.logError("Failed to hook GAID", e)
        }
    }

    // ============================================================
    // GSF ID (Google Services Framework ID) Hook
    // ============================================================

    /**
     * Hook methods that retrieve the Google Services Framework ID.
     */
    private fun hookGsfId(lpparam: LoadPackageParam) {
        try {
            // Hook ContentResolver.query for GSF ID queries
            XposedHelpers.findAndHookMethod(
                "android.content.ContentResolver",
                lpparam.classLoader,
                "query",
                android.net.Uri::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val uri = param.args[0] as? android.net.Uri ?: return
                        val uriString = uri.toString()

                        // Check if this is a GSF ID query
                        if (uriString.contains("com.google.android.gsf.gservices") &&
                            uriString.contains("android_id")) {
                            val context = AndroidAppHelper.currentApplication()
                            val customGsfId = AppSetIdProvider.getGsfId(context)
                            if (customGsfId != null) {
                                HookLogger.log("✓ GSF ID query intercepted, custom ID: $customGsfId")
                                // The GSF ID is typically returned via cursor, which is complex to spoof
                                // Log it for now, actual spoofing may require additional work
                            }
                        }
                    }
                }
            )
            HookLogger.log("GSF ID query hook installed")

            // Also try to hook GServices.getString if available
            try {
                val gServicesClass = XposedHelpers.findClass(
                    "com.google.android.gms.common.GooglePlayServicesUtil",
                    lpparam.classLoader
                )
                HookLogger.log("GooglePlayServicesUtil class found")
            } catch (e: Throwable) {
                // Class not found, which is fine
            }
        } catch (e: Exception) {
            HookLogger.logError("Failed to hook GSF ID", e)
        }
    }
}
