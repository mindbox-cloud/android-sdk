package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest
import com.google.gson.Gson
import org.koin.core.component.inject


internal data class ViewProductCategoryNode(
    override val type: String,
    val kind: KindSubstring,
    val value: String,
) : OperationNodeBase(type) {

    private val mobileConfigRepository: MobileConfigRepository by inject()
    private val gson: Gson by inject()

    override suspend fun filterEvent(event: InAppEventType): Boolean {
        return inAppEventManager.isValidViewProductCategoryEvent(event)
    }

    override fun checkTargeting(): Boolean {
        val event = lastEvent as? InAppEventType.OrdinalEvent ?: return false
        val body = gson.fromJson(event.body, OperationBodyRequest::class.java)

        val externalIds = body?.viewProductCategory?.productCategory?.ids?.ids?.values
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

    override suspend fun getOperationsSet(): Set<String> =
        mobileConfigRepository.getOperations()[OperationName.VIEW_CATEGORY]?.systemName?.let {
            setOf(it)
        } ?: setOf()
}