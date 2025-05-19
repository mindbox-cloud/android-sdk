package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDto
import cloud.mindbox.mobile_sdk.models.operation.response.SlidingExpirationDto

internal class SettingsStub {
    companion object {
        fun getSlidingExpiration(
            timeSpan: String = "",
            pushTokenKeepalive: String? = null
        ): InAppConfigResponse =
            InAppConfigResponse(
                inApps = null,
                monitoring = null,
                settings = SettingsDto(
                    operations = emptyMap(),
                    ttl = null,
                    slidingExpiration = SlidingExpirationDto(
                        config = timeSpan,
                        pushTokenKeepalive = pushTokenKeepalive
                    )
                ),
                abtests = null
            )
    }
}
