package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.CallbackRepository

internal class CallbackInteractorImpl(private val callbackRepository: CallbackRepository) :
    CallbackInteractor {

    override fun shouldCopyString(userString: String): Boolean {
        return callbackRepository.validateUserString(userString)
    }

    override fun isValidUrl(url: String): Boolean {
        return callbackRepository.isValidUrl(url)
    }
}
