package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class CatalogProductListResponse(
    @SerializedName("processingStatus") public val processingStatus: ProcessingStatusResponse? = null,
    @SerializedName("items") public val items: List<ItemResponse>? = null
) {

    override fun toString(): String = "CatalogProductListResponse(processingStatus=$processingStatus, " +
        "items=$items)"
}
