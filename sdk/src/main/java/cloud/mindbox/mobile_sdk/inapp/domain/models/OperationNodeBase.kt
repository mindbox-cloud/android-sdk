package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

internal abstract class OperationNodeBase(override val type: String) : TreeTargeting(type) {

    protected var lastEvent: InAppEventType? = null
    private val operationNodeScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + Mindbox.coroutineExceptionHandler)

    init {
        operationNodeScope.launch {
            MindboxEventManager.eventFlow.filter { filterEvent(it) }
                .collect { inAppEventType ->
                    lastEvent = inAppEventType
                }
        }
    }

    abstract suspend fun filterEvent(event: InAppEventType): Boolean

    override suspend fun fetchTargetingInfo() {
        // do nothing
    }

    override fun hasSegmentationNode(): Boolean = false

    override fun hasGeoNode(): Boolean = false

    override fun hasOperationNode(): Boolean = true
}