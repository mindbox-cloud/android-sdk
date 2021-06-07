package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class CatalogProductListResponse(
    @SerializedName("processingStatus") val processingStatus: ProcessingStatusResponse? = null,
    @SerializedName("items") val items: List<ItemResponse>? = null
) {

    override fun toString() = "CatalogProductListResponse(processingStatus=$processingStatus, " +
            "items=$items)"

}
