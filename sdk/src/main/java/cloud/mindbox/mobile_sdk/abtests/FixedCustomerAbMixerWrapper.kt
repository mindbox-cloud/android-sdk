package cloud.mindbox.mobile_sdk.abtests

import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

internal class FixedCustomerAbMixerWrapper(private val fallbackMixer: CustomerAbMixer) :
    CustomerAbMixer {

    override fun stringModulusHash(identifier: String, salt: String): Int {
        return MindboxPreferences.mixerFixedHash
            .takeIf { it in 0..99 }
            ?.let {
                this@FixedCustomerAbMixerWrapper.mindboxLogW("Mixer use fixed hash $it!")
                return it
            } ?: fallbackMixer.stringModulusHash(identifier, salt)
    }
}
