# ==============================================================================
# LIFEOS — Production R8 & ProGuard Optimization Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Android Architecture & Jetpack Compose
# ------------------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Compose runtime and compiler generated classes
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep ViewModel providers and factories
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ------------------------------------------------------------------------------
# 2. Room Database & SQLite Architecture (Schema v5)
# ------------------------------------------------------------------------------
# Prevent R8 from obfuscating Room Entity data classes and DAOs
-keep class com.lifeos.app.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 3. Android System Services & Broadcast Receivers
# ------------------------------------------------------------------------------
# Preserve LifeNotificationListener and standalone broadcast receivers
-keep class com.lifeos.app.service.LifeNotificationListener { *; }
-keep class com.lifeos.app.service.TestNotificationReceiver { *; }
-keep class com.lifeos.app.service.GeofenceBroadcastReceiver { *; }
-keep class com.lifeos.app.service.TtlCleanupWorker { *; }

# Preserve WorkManager Worker constructors
-keepclasseswithmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclasseswithmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ------------------------------------------------------------------------------
# 4. Google Play Services Location & Geofencing
# ------------------------------------------------------------------------------
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.location.**

# ------------------------------------------------------------------------------
# 5. Core Engines & Security Firewall Models
# ------------------------------------------------------------------------------
-keep class com.lifeos.app.engine.** { *; }
-keep class com.lifeos.app.security.** { *; }

# Preserve Kotlin Reflection & Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------------------------
# 6. Enum & Sealed Class Preservation
# ------------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
