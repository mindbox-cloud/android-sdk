package cloud.mindbox.mobile_sdk.models

@JvmInline
internal value class PlaceKey private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        fun of(raw: String): PlaceKey = PlaceKey(raw.trim().lowercase())
    }
}
