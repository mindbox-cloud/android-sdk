package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest
import com.google.gson.Gson
import org.koin.core.component.inject


internal data class ViewProductCategoryNode(
    override val type: String,
    val kind: KindSubstring,
    val value: String
) : OperationNodeBase(type) {

    private val mobileConfigRepository: MobileConfigRepository by inject()
    private val gson: Gson by inject()

    override suspend fun filterEvent(event: InAppEventType): Boolean {
        return event is InAppEventType.OrdinalEvent
    }

    override fun checkTargeting(): Boolean {
        val event = lastEvent as InAppEventType.OrdinalEvent
        val body = gson.fromJson(event.body, OperationBodyRequest::class.java)

        val externalId = body?.viewProductCategory?.productCategory?.ids?.ids?.get(value) ?: return false

        return lastEvent?.name?.let { category ->
            return when(kind) {
                KindSubstring.SUBSTRING -> category.contains(externalId, ignoreCase = true)
                KindSubstring.NOT_SUBSTRING -> !category.contains(externalId, ignoreCase = true)
                KindSubstring.STARTS_WITH -> category.startsWith(externalId, ignoreCase = true)
                KindSubstring.ENDS_WITH -> category.endsWith(externalId, ignoreCase = true)
            }
        } ?: false
    }

    override suspend fun getOperationsSet(): Set<String> =
        mobileConfigRepository.getOperations()[OperationName.VIEW_CATEGORY]?.systemName?.let {
            setOf(it)
        } ?: setOf()
}