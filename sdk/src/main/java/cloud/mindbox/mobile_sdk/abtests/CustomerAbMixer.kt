package cloud.mindbox.mobile_sdk.abtests

internal interface CustomerAbMixer {
    fun stringModulusHash(identifier: String, salt: String): Int
}
