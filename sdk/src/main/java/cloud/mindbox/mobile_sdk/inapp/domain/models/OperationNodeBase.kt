package cloud.mindbox.mobile_sdk.inapp.domain.models

internal abstract class OperationNodeBase(override val type: String) : TreeTargeting(type) {

    override suspend fun fetchTargetingInfo(data: TargetingData) {
        return
    }

    override fun hasSegmentationNode(): Boolean = false

    override fun hasGeoNode(): Boolean = false

    override fun hasOperationNode(): Boolean = true
}