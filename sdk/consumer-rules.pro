-keepclassmembers class cloud.mindbox.mobile_sdk.models** { *; }
-keep class cloud.mindbox.mobile_sdk.MindboxConfiguration { *; }
-keep class cloud.mindbox.mobile_sdk.pushes.PushAction { *; }
-keepclassmembers class cloud.mindbox.mobile_sdk.inapp.data.dto.** { *; }
-keep class cloud.mindbox.mobile_sdk.inapp.domain.models** { *; }

-keepclasseswithmembers,allowobfuscation class cloud.mindbox.** {
    @com.google.gson.annotations.SerializedName <fields>;
    <init>(...);
}

-keepclassmembers class cloud.mindbox.** {
    <init>(...);
    <fields>;
}
