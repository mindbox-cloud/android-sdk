package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.validators.JsonValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.UrlValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.XmlValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.CallbackRepository

internal class CallbackRepositoryImpl(
    private val xmlValidator: XmlValidator,
    private val jsonValidator: JsonValidator,
    private val urlValidator: UrlValidator
) : CallbackRepository {

    override fun validateUserString(userString: String): Boolean {
        return !(xmlValidator.isValid(userString) or jsonValidator.isValid(userString) or urlValidator.isValid(
            userString
        ))
    }

    override fun isValidUrl(url: String): Boolean {
        return urlValidator.isValid(url)
    }

}