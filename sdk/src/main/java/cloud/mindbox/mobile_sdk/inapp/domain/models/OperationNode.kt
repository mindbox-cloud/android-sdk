package cloud.mindbox.mobile_sdk.inapp.domain.models


internal data class OperationNode(
    override val type: String,
    val systemName: String,
) : OperationNodeBase(type) {

    override suspend fun fetchTargetingInfo(data: TargetingData) {
        return
    }

    override fun checkTargeting(data: TargetingData): Boolean {
        if (data !is TargetingData.OperationName) return false
        return data.triggerEventName.equals(systemName, true)
    }

    override suspend fun getOperationsSet(): Set<String> {
        return setOf(systemName)
    }
}