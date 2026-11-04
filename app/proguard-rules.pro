# BARQ! Wear Proguard / R8 rules

# Keep generic signatures & annotations for Gson TypeToken reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn sun.misc.**

# Gson TypeToken rules
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }

# Keep data models used by Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class yt.dsh.barq_wear.data.** { *; }
-keepclassmembers class yt.dsh.barq_wear.data.** { *; }

# OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

