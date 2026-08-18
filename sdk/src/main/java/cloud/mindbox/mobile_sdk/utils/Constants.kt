package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.models.Milliseconds

internal object Constants {
    internal const val SDK_VERSION_NUMERIC = 13
    internal const val TYPE_JSON_NAME = "\$type"
    internal const val POST_NOTIFICATION = "android.permission.POST_NOTIFICATIONS"
    internal const val NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    internal const val APP_PACKAGE_NAME = "app_package"
    internal const val APP_UID_NAME = "app_uid"
    internal const val SCHEME_PACKAGE = "package"
    internal const val SDK_VERSION_CODE = 4

    internal object WebView {
        internal val readyTimeout = Milliseconds(7_000L)
    }

    internal object Embedded {
        internal val defaultConfigTimeout = Milliseconds(30_000L)
    }
}
