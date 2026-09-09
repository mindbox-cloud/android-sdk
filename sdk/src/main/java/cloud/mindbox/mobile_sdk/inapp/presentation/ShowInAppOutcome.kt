package cloud.mindbox.mobile_sdk.inapp.presentation

internal sealed interface ShowInAppOutcome {
    data object Shown : ShowInAppOutcome

    data class NotShown(val reason: ShowInAppFailure) : ShowInAppOutcome
}

internal enum class ShowInAppFailure(val bridgeReason: String) {
    UNKNOWN_INAPP("unknown_inapp"),
    SOURCE_DISMISSED("source_dismissed"),
    SHOW_FAILED("show_failed"),
}

internal fun interface OnShowInAppOutcome {
    fun onOutcome(outcome: ShowInAppOutcome)
}
