package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal interface InAppProcessingManager {

    /**
     * [selectVariant] is the caller's statement of which variant it is going to render — the content
     * fetched for a candidate is that variant's, not simply the first one. `null` skips the candidate.
     */
    suspend fun chooseInAppToShow(
        inApps: List<InApp>,
        triggerEvent: InAppEventType,
        selectVariant: (InApp) -> InAppType? = { inApp -> inApp.form.variants.firstOrNull() },
    ): InApp?

    suspend fun sendTargetedInApp(inApp: InApp, triggerEvent: InAppEventType)

    /** Sends `Inapp.Targeting` without re-checking anything: the caller has already matched targeting. */
    fun sendTargetedInApp(inApp: InApp)
}
