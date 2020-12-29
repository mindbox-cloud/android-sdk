package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

class Configuration(builder: Builder) {
    internal val installationId: String = builder.installationId
    internal val deviceId: String = builder.deviceId
    internal val endpoint: String = builder.endpoint

    class Builder(var endpoint: String) {
        var installationId: String = MindboxPreferences.installationId ?: ""
        var deviceId: String = MindboxPreferences.userAdid ?: ""

        fun setDeviceId(deviceId: String): Builder {
            this.deviceId = deviceId
            return this
        }

        fun setInstallationId(installationId: String): Builder {
            this.installationId = installationId
            return this
        }

        fun build(): Configuration {
            return Configuration(this)
        }
    }
}