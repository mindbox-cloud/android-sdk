package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeErrorCode

internal sealed interface ShowInAppOutcome {
    data object Shown : ShowInAppOutcome

    data class NotShown(val code: BridgeErrorCode) : ShowInAppOutcome
}

internal fun interface OnShowInAppOutcome {
    fun onOutcome(outcome: ShowInAppOutcome)
}
