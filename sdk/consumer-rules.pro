# Keep model classes
-keepclassmembers class cloud.mindbox.mobile_sdk.models** { *; }
-keepclassmembers enum cloud.mindbox.mobile_sdk.models** { *; }
-keep class cloud.mindbox.mobile_sdk.MindboxConfiguration { *; }
-keep class cloud.mindbox.mobile_sdk.pushes.PushAction { *; }
-keep class cloud.mindbox.mobile_sdk.inapp.data.dto** { *; }

-keep public class * extends android.preference.Preference