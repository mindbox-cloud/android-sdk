package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.SdkValidation

internal sealed class OperationsDomainConfigPolicyAction {
    data class Save(val value: String) : OperationsDomainConfigPolicyAction()

    object Clear : OperationsDomainConfigPolicyAction()

    object Keep : OperationsDomainConfigPolicyAction()
}

internal object OperationsDomainConfigPolicy {

    fun action(raw: String?, currentlyStored: String?): OperationsDomainConfigPolicyAction {
        val value = raw?.trim()?.takeIf { it.isNotBlank() }
            ?: return currentlyStored?.let {
                OperationsDomainConfigPolicyAction.Clear
            } ?: OperationsDomainConfigPolicyAction.Keep

        if (!SdkValidation.isValidDomain(value)) return OperationsDomainConfigPolicyAction.Keep

        return if (value == currentlyStored) {
            OperationsDomainConfigPolicyAction.Keep
        } else {
            OperationsDomainConfigPolicyAction.Save(value)
        }
    }
}
