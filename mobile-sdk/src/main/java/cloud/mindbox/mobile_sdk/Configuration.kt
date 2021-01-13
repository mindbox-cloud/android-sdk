package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

class Configuration(builder: Builder) {
    internal val installationId: String = builder.installationId
    internal val deviceId: String = builder.deviceId
    internal val endpoint: String = builder.endpoint
    internal val domain: String = builder.domain
    internal val packageName: String = builder.packageName
    internal val versionName: String = builder.versionName
    internal val versionCode: String = builder.versionCode

    class Builder(private val context: Context, val domain: String, val endpoint: String) {
        var installationId: String = MindboxPreferences.installationId ?: ""
        var deviceId: String = MindboxPreferences.userAdid ?: ""
        internal var packageName: String = PLACEHOLDER_APP_PACKAGE_NAME
        internal var versionName: String = PLACEHOLDER_APP_VERSION_NAME
        internal var versionCode: String = PLACEHOLDER_APP_VERSION_CODE

        companion object {
            private const val PLACEHOLDER_APP_PACKAGE_NAME = "Unknown package name"
            private const val PLACEHOLDER_APP_VERSION_NAME = "Unknown version"
            private const val PLACEHOLDER_APP_VERSION_CODE = "?"
        }

        fun setDeviceId(deviceId: String): Builder {
            this.deviceId = deviceId
            return this
        }

        fun setInstallationId(installationId: String): Builder {
            this.installationId = installationId
            return this
        }

        fun build(): Configuration {
            generateAppInfo(context)
            return Configuration(this)
        }

        private fun generateAppInfo(context: Context) {
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
                packageName = packageInfo.packageName.trim()
                this.versionName = packageInfo.versionName?.trim() ?: PLACEHOLDER_APP_PACKAGE_NAME
                this.versionCode =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString().trim()
                    } else {
                        packageInfo.versionCode.toString().trim()
                    }
            } catch (e: Exception) {
                Logger.e(this, "Getting app info failed. Identified as an unknown application")
            }
        }
    }
}