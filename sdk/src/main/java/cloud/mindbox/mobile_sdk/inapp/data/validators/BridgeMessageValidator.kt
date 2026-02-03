package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeMessage
import cloud.mindbox.mobile_sdk.logger.mindboxLogW

@OptIn(InternalMindboxApi::class)
internal class BridgeMessageValidator : Validator<BridgeMessage?> {
    override fun isValid(item: BridgeMessage?): Boolean {
        item ?: return false

        runCatching {
            if (item.id.isBlank()) {
                mindboxLogW("BridgeMessage id is empty")
                return false
            }

            if (item.type !in listOf(
                    BridgeMessage.TYPE_REQUEST,
                    BridgeMessage.TYPE_RESPONSE,
                    BridgeMessage.TYPE_ERROR
                )
            ) {
                mindboxLogW("BridgeMessage type ${item.type} is not supported")
                return false
            }

            if (item.action.name.isEmpty()) {
                mindboxLogW("BridgeMessage action is empty")
                return false
            }

            if (item.timestamp <= 0L) {
                mindboxLogW("BridgeMessage timestamp must be positive")
                return false
            }

            if (item.version > BridgeMessage.VERSION) {
                mindboxLogW("BridgeMessage version ${item.version} is not supported")
                return false
            }
        }.onFailure { error ->
            mindboxLogW("BridgeMessage validation error: $error")
            return false
        }

        return true
    }
}
