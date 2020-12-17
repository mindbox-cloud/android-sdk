package cloud.mindbox.mobile_sdk

import java.util.*


class Configuration {

    fun getInstallationId(): String? {
        return MindboxPreferences.installationId
    }

    fun setInstallationId(value: UUID) {
        MindboxPreferences.installationId = value.toString()
    }

    fun getDeviceUuid(): String? {
        return MindboxPreferences.userAdid
    }

    fun setDeviceUuid(value: UUID) {
        MindboxPreferences.userAdid = value.toString()
    }
}