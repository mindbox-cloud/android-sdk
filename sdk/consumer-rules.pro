# Keep model classes
-keepclassmembers class cloud.mindbox.mobile_sdk.models** { *; }
-keepclassmembers enum cloud.mindbox.mobile_sdk.models** { *; }
-keep class cloud.mindbox.mobile_sdk.MindboxConfiguration { *; }

# Gson
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.preference.Preference

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# WorkManager
# Rendescript
-keepclasseswithmembernames class * {
   native <methods>;
}

# Volley
-dontwarn com.android.volley.error.**
-keep class com.android.volley.Response$* { *; }
-keep class com.android.volley.Request$* { *; }
-keep class org.apache.commons.logging.*