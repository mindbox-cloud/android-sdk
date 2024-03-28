package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity

internal interface MindboxNotificationManager {

    fun isNotificationEnabled(): Boolean

    fun openNotificationSettings(activity: Activity)

    fun requestPermission(activity: Activity)

    var shouldOpenSettings: Boolean

}