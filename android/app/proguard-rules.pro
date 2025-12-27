# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes for Moshi
-keep class com.smartpresence.idukay.data.remote.dto.** { *; }
-keep class com.smartpresence.idukay.data.local.entity.** { *; }

# Keep Retrofit interfaces
-keep interface com.smartpresence.idukay.data.remote.api.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
