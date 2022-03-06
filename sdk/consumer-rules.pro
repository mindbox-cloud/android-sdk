# Keep model classes
-keepclassmembers class cloud.mindbox.mobile_sdk.models** { *; }
-keepclassmembers enum cloud.mindbox.mobile_sdk.models** { *; }
-keep class cloud.mindbox.mobile_sdk.MindboxConfiguration { *; }

-keep public class * extends android.preference.Preference