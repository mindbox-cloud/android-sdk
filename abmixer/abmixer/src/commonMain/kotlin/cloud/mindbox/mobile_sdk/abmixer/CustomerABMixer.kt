package cloud.mindbox.mobile_sdk.abmixer

interface CustomerAbMixer {

    companion object {
        fun impl(): CustomerAbMixer = CustomerAbMixerImpl()
    }

    fun stringModulusHash(identifier: String, salt: String): Int
}

