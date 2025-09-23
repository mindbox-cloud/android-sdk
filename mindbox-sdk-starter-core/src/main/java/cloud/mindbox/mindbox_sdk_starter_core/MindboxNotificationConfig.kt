package cloud.mindbox.mindbox_sdk_starter_core

import android.app.Activity

/**
 * A dataclass for internal sdk work only. Do not use it
 * */

public data class MindboxNotificationConfig(
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
    val smallIcon: Int,
    val activities: Map<String, Class<out Activity>> = emptyMap(),
    val defaultActivity: Class<out Activity>?
)
