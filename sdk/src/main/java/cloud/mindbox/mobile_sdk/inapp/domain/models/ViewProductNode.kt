package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest
import com.google.gson.Gson

internal data class ViewProductNode(
    override val type: String,
    val kind: KindSubstring,
    val value: String,
) : OperationNodeBase(type) {

    private val mobileConfigRepository by mindboxInject { mobileConfigRepository }
    private val gson: Gson by mindboxInject { gson }

    override fun checkTargeting(data: TargetingData): Boolean {
        if (data !is TargetingData.OperationBody) return false
        val body = gson.fromJson(data.operationBody, OperationBodyRequest::class.java)

        val externalIds = body?.viewProductRequest?.product?.ids?.ids?.values
            ?.filterNotNull() ?: return false

        return when (kind) {
            KindSubstring.SUBSTRING -> externalIds.any { externalId ->
                externalId.contains(value, ignoreCase = true)
            }
            KindSubstring.NOT_SUBSTRING -> externalIds.any { externalId ->
                !externalId.contains(value, ignoreCase = true)
            }
            KindSubstring.STARTS_WITH -> externalIds.any { externalId ->
                externalId.startsWith(value, ignoreCase = true)
            }
            KindSubstring.ENDS_WITH -> externalIds.any { externalId ->
                externalId.endsWith(value, ignoreCase = true)
            }
        }
    }

    override suspend fun getOperationsSet(): Set<String> {
        return mobileConfigRepository.getOperations()[OperationName.VIEW_PRODUCT]?.systemName?.let {
            setOf(it)
        } ?: setOf()
    }
}
