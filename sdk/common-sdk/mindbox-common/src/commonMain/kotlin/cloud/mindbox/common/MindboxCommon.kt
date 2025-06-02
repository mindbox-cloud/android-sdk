package cloud.mindbox.common

import cloud.mindbox.mobile_sdk.abmixer.CustomerAbMixer

public object MindboxCommon {

    val version: String = "1.0.0"

    private fun check() {
        CustomerAbMixer.impl()
    }

}

internal enum class MindboxCommonPlatform {
    ANDROID,
    IOS,
    UNKNOWN;
}

internal expect fun getPlatform(): MindboxCommonPlatform
