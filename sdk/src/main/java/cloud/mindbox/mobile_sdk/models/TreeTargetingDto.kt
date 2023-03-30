package cloud.mindbox.mobile_sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Тargeting types
 **/
internal sealed class TreeTargetingDto {

    internal data class TrueNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
    ) : TreeTargetingDto() {
        companion object {
            const val TRUE_JSON_NAME = "true"
        }
    }

    internal data class IntersectionNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("nodes")
        val nodes: List<TreeTargetingDto?>?,
    ) : TreeTargetingDto() {
        companion object {
            const val AND_JSON_NAME = "and"
        }
    }

    internal data class UnionNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("nodes")
        val nodes: List<TreeTargetingDto?>?,
    ) : TreeTargetingDto() {
        companion object {
            const val OR_JSON_NAME = "or"
        }
    }

    internal data class SegmentNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("segmentationExternalId")
        val segmentationExternalId: String?,
        @SerializedName("segmentationInternalId")
        val segmentationInternalId: String?,
        @SerializedName("segmentExternalId")
        val segmentExternalId: String?,
    ) : TreeTargetingDto() {
        companion object {
            const val SEGMENT_JSON_NAME = "segment"
        }
    }

    internal data class CountryNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("ids")
        val ids: List<String?>?,
    ) : TreeTargetingDto() {
        companion object {
            const val COUNTRY_JSON_NAME = "country"
        }
    }

    internal data class CityNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("ids")
        val ids: List<String>?,
    ) : TreeTargetingDto() {
        companion object {
            const val CITY_JSON_NAME = "city"
        }
    }

    internal data class RegionNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("ids")
        val ids: List<String?>?,
    ) : TreeTargetingDto() {
        companion object {
            const val REGION_JSON_NAME = "region"
        }
    }

    internal data class OperationNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("systemName")
        val systemName: String?,
    ) : TreeTargetingDto() {
        companion object {
            const val API_METHOD_CALL_JSON_NAME = "apiMethodCall"
        }
    }

    internal data class ViewProductCategoryNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("value")
        val value: String?,
    ) : TreeTargetingDto() {
        companion object {
            const val VIEW_PRODUCT_CATEGORY_ID_JSON_NAME = "viewProductCategoryId"
        }
    }

    internal data class ViewProductCategoryInNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("values")
        val values: List<ValueDto>?,
    ) : TreeTargetingDto() {
        companion object {
            const val VIEW_PRODUCT_CATEGORY_ID_IN_JSON_NAME = "viewProductCategoryIdIn"
        }

        internal data class ValueDto(
            @SerializedName("id")
            val id: String?,
            @SerializedName("externalId")
            val externalId: String?,
            @SerializedName("externalSystemName")
            val externalSystemName: String?,
        )
    }

    companion object {
        const val TYPE_JSON_NAME = "\$type"
    }
}