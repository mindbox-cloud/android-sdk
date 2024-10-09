package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest

internal data class ViewProductCategoryInNode(
    override val type: String,
    val kind: KindAny,
    val values: List<Value>,
) : OperationNodeBase(type) {

    private val mobileConfigRepository by mindboxInject { mobileConfigRepository }
    private val gson by mindboxInject { gson }

    override fun checkTargeting(data: TargetingData): Boolean {
        if (data !is TargetingData.OperationBody) return false

        val body = data.operationBody?.let { operationBody ->
            gson.fromJson(operationBody, OperationBodyRequest::class.java)
        } ?: return false

        val ids = body.viewProductCategory?.productCategory?.ids?.ids?.toMap()

        return ids?.let {
            when (kind) {
                KindAny.ANY -> ids.any { (externalSystemName, externalId) ->
                    values.any { value ->
                        value.externalId.equals(externalId, true) &&
                            value.externalSystemName.equals(externalSystemName, true)
                    }
                }
                KindAny.NONE -> ids.none { (externalSystemName, externalId) ->
                    values.any { value ->
                        value.externalId.equals(externalId, true) &&
                            value.externalSystemName.equals(externalSystemName, true)
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
