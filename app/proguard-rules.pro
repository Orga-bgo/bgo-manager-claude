# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Hilt classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================================
# Xposed Hook Classes
# ============================================================

# Main Hook class (entry point defined in xposed_init)
-keep class com.mgomanager.app.xposed.MonopolyGoHook {
    public *;
}

# Keep all Xposed package classes
-keep class com.mgomanager.app.xposed.** { *; }

# ============================================================
# Database Entities for Xposed Reflection Access
# ============================================================

# Xposed hook accesses database via reflection
-keep class com.mgomanager.app.data.local.database.** { *; }
-keep class com.mgomanager.app.data.local.database.entities.** { *; }
-keep class com.mgomanager.app.data.local.database.dao.** { *; }

# Room DAO methods (especially the synchronous one)
-keep @androidx.room.Dao class * { *; }

# ============================================================
# Xposed API
# ============================================================

# Xposed interfaces and callbacks
-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# ============================================================
# Google Play Services (for AppSetIdClient hook)
# ============================================================

-keep class com.google.android.gms.appset.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.android.gms.**
