package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SdkVersion
import cloud.mindbox.mobile_sdk.utils.Constants

internal class SdkVersionValidator : Validator<SdkVersion?> {

    override fun isValid(item: SdkVersion?): Boolean =
        item?.let { sdkVersion ->
            val minVersionValid = sdkVersion.minVersion?.let { min ->
                min <= Constants.SDK_VERSION_NUMERIC
            } ?: true
            val maxVersionValid = sdkVersion.maxVersion?.let { max ->
                max >= Constants.SDK_VERSION_NUMERIC
            } ?: true
            return minVersionValid && maxVersionValid
        } ?: false
}
