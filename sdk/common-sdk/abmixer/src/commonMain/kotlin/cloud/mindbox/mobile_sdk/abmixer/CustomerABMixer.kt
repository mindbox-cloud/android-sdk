package cloud.mindbox.mobile_sdk.abmixer

interface CustomerAbMixer {

    companion object {
        @Suppress("UNUSED")
        fun impl(): CustomerAbMixer = CustomerAbMixerImpl()
    }

    fun stringModulusHash(identifier: String, salt: String): Int
}

