package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.models.InAppEventType


internal data class OperationNode(
    override val type: String,
    val systemName: String,
) : OperationNodeBase(type) {

    private val inAppEventManager by lazy {
        MindboxKoin.koin.get<InAppEventManager>()
    }

    override fun checkTargeting(): Boolean {
        return lastEvent?.name?.equals(systemName, true) ?: false
    }

    override suspend fun getOperationsSet(): Set<String> {
        return setOf(systemName)
    }

    override suspend fun filterEvent(event: InAppEventType): Boolean {
        return inAppEventManager.isValidOperationalEvent(event)
    }
}