package cloud.mindbox.mobile_sdk.abtests

interface CustomerAbMixer {
    fun stringModulusHash(identifier: String, salt: String): Int
}
