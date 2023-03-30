package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest
import com.google.gson.Gson
import org.koin.core.component.inject


internal data class ViewProductCategoryInNode(
    override val type: String,
    val kind: KindAny,
    val values: List<Value>,
) : OperationNodeBase(type) {

    private val mobileConfigRepository: MobileConfigRepository by inject()
    private val gson: Gson by inject()

    override suspend fun filterEvent(event: InAppEventType): Boolean {
        return inAppEventManager.isValidViewProductCategoryEvent(event)
    }

    override fun checkTargeting(): Boolean {
        val event = lastEvent as? InAppEventType.OrdinalEvent ?: return false
        val body = gson.fromJson(event.body, OperationBodyRequest::class.java)

        val ids = body?.viewProductCategory?.productCategory?.ids?.ids?.toMap()

        return ids?.let {
            when (kind) {
                KindAny.ANY -> ids.any { (externalSystemName, externalId) ->
                    values.any { value ->
                        value.externalId.equals(externalId, true)
                                && value.externalSystemName.equals(externalSystemName, true)
                    }
                }
                KindAny.NONE -> ids.none { (externalSystemName, externalId) ->
                    values.any { value ->
                        value.externalId.equals(externalId, true)
                                && value.externalSystemName.equals(externalSystemName, true)
                    }
                }
            }
        } ?: false

    }

    override suspend fun getOperationsSet(): Set<String> =
        mobileConfigRepository.getOperations()[OperationName.VIEW_CATEGORY]?.systemName?.let {
            setOf(it)
        } ?: setOf()

    internal data class Value(
        val id: String,
        val externalId: String,
        val externalSystemName: String,
    )
}